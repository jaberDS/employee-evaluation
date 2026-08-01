"""
Service de reconnaissance faciale ATB.

Rôle : transformer une image en vecteur, et dire si une séquence de trames
correspond bien à une personne vivante qui exécute les consignes demandées.

Ce service ne décide *jamais* de l'identité : il renvoie des embeddings et des
verdicts de vivacité. La comparaison au gabarit stocké et le seuil de décision
restent côté Spring, avec le reste de la logique d'authentification.

Le client envoie une RAFALE de trames par consigne, pas une seule. Un clignement
dure 100 à 400 ms : une trame unique prise à la fin de la consigne le rate quasi
systématiquement. On juge donc chaque consigne sur l'extremum de sa rafale.

À n'exposer que sur la boucle locale — aucune authentification n'est prévue ici.
"""
from __future__ import annotations

import base64
import binascii
import logging
import os
from contextlib import asynccontextmanager
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from insightface.app import FaceAnalysis
from pydantic import BaseModel, Field

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("face-service")

# ─── Paramètres ────────────────────────────────────────────────────────────

MODEL_NAME = os.getenv("FACE_MODEL", "buffalo_l")
# 320 plutôt que 640 : la détection domine le temps de traitement et son coût
# croît avec le carré de cette taille. Le visage d'un selfie occupe une large
# part du cadre, donc 320 le trouve sans peine — mesuré ~4× plus rapide, ce qui
# ramène une cérémonie complète sous le délai d'attente de Spring.
DET_SIZE = int(os.getenv("FACE_DET_SIZE", "320"))
# Une image de 640×480 en JPEG qualité 0.8 pèse ~60 Ko, soit ~80 Ko en base64.
# 4 Mo laisse une marge confortable même pour du 1080p.
MAX_IMAGE_BYTES = int(os.getenv("FACE_MAX_IMAGE_BYTES", str(4 * 1024 * 1024)))
# Rafales obligent : 3 consignes × 8 trames laisse encore de la marge.
MAX_FRAMES = int(os.getenv("FACE_MAX_FRAMES", "48"))

# Seuils de vivacité, tous RELATIFS à la trame neutre de la même personne.
#
# Une mesure absolue ne tient pas : sur six visages de référence, l'ouverture
# des yeux grands ouverts s'étale de 0,12 à 0,67 selon la morphologie et la pose.
# Un seuil fixe classerait la moitié des gens comme « en train de cligner ».
# On compare donc chacun à lui-même, ce qui s'auto-calibre.

# L'œil doit se fermer à moins de 82 % de son ouverture au repos.
# Avec une rafale on mesure le minimum de la séquence, donc on attrape le creux
# réel du clignement et non un instant quelconque : le seuil peut rester lâche
# sans ouvrir la porte à la photo figée, qui elle ne bouge pas du tout (1,00).
# Relevé de 0,78 : un clignement mesuré valait 0,74 du repos, marge trop mince
# quand la rafale tombe légèrement à côté du creux.
BLINK_CLOSE_RATIO = float(os.getenv("FACE_BLINK_RATIO", "0.82"))
# Rotation de tête d'au moins 10° par rapport à la pose de départ.
YAW_DELTA_DEG = float(os.getenv("FACE_YAW_DELTA_DEG", "10.0"))
# Déplacement du nez, en fraction de la distance inter-oculaire. Sert de mesure
# de repli quand `pose` est absent, et de recoupement quand il est présent.
NOSE_DELTA_RATIO = float(os.getenv("FACE_NOSE_DELTA", "0.11"))
# La bouche doit s'élargir d'au moins 3 %. Mesuré : un sourire franc ne gagne
# que 5 % en largeur, donc 5 % de seuil ne laissait aucune marge.
SMILE_WIDEN_RATIO = float(os.getenv("FACE_SMILE_WIDEN", "1.03"))
# Le rapport largeur/hauteur de bouche, lui, gagne beaucoup plus : 12 % est un
# seuil confortable qu'un visage au repos ne franchit pas.
SMILE_RATIO_GAIN = float(os.getenv("FACE_SMILE_RATIO_GAIN", "1.12"))

# Exiger le BON côté de rotation suppose que le signe du yaw rendu par le modèle
# est celui qu'on croit — il varie selon les versions d'InsightFace. Par défaut
# on se contente de l'amplitude : un mouvement franc de la tête suffit déjà à
# écarter la photo et la vidéo rejouée, dont la séquence de consignes ne peut
# pas coïncider. Passer à 1 une fois le signe vérifié sur le parc réel.
TURN_DIRECTION_STRICT = os.getenv("FACE_TURN_STRICT", "0") == "1"

MIN_DET_SCORE = float(os.getenv("FACE_MIN_DET_SCORE", "0.55"))
# Cohérence d'identité entre les trames d'une même séquence. Volontairement bas :
# une tête tournée à 30° perd naturellement de la similarité avec sa propre vue
# de face. Il s'agit d'écarter un changement de personne, pas de re-vérifier
# l'identité — ça, c'est le rôle du seuil de correspondance côté Java.
IDENTITY_CONSISTENCY_THRESHOLD = float(os.getenv("FACE_IDENTITY_CONSISTENCY", "0.32"))
# Au-delà de ce décalage du nez, la trame est trop de profil pour entrer dans le
# gabarit : un embedding moyenné sur des vues tournées vieillit mal.
FRONTAL_NOSE_LIMIT = float(os.getenv("FACE_FRONTAL_NOSE_LIMIT", "0.09"))

_analyzer: Optional[FaceAnalysis] = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Charge les modèles une seule fois : l'init coûte plusieurs secondes."""
    global _analyzer
    logger.info("Chargement du modèle %s (det_size=%d)...", MODEL_NAME, DET_SIZE)
    analyzer = FaceAnalysis(name=MODEL_NAME, providers=["CPUExecutionProvider"])
    analyzer.prepare(ctx_id=-1, det_size=(DET_SIZE, DET_SIZE))
    _analyzer = analyzer
    logger.info("Modèle prêt.")
    yield
    _analyzer = None


app = FastAPI(
    title="ATB Face Service",
    version="2.0.0",
    description="Embeddings ArcFace et détection de vivacité active par rafales.",
    lifespan=lifespan,
)


# ─── Schémas ───────────────────────────────────────────────────────────────

class EmbedRequest(BaseModel):
    image: str = Field(..., description="JPEG/PNG encodé en base64 (data-URL toléré)")


class BoundingBox(BaseModel):
    x: int
    y: int
    width: int
    height: int


class EmbedResponse(BaseModel):
    embedding: List[float]
    quality: float
    detScore: float
    box: BoundingBox


class Frame(BaseModel):
    action: str
    image: str


class LivenessRequest(BaseModel):
    frames: List[Frame]


class StepVerdict(BaseModel):
    action: str
    passed: bool
    reason: str


class LivenessResponse(BaseModel):
    live: bool
    identityConsistent: bool
    steps: List[StepVerdict]
    embedding: Optional[List[float]] = None
    quality: float = 0.0
    reason: str = ""


# ─── Utilitaires image ─────────────────────────────────────────────────────

def _decode_image(payload: str) -> np.ndarray:
    """base64 (avec ou sans préfixe data-URL) → matrice BGR."""
    if not payload:
        raise HTTPException(status_code=400, detail="Image vide")

    if payload.startswith("data:"):
        _, _, payload = payload.partition(",")

    try:
        raw = base64.b64decode(payload, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=400, detail="Base64 invalide") from exc

    if len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image trop volumineuse")

    image = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Format d'image non reconnu")
    return image


def _largest_face(image: np.ndarray):
    """
    Retient le plus grand visage. Sur un selfie d'authentification, un visage
    en arrière-plan ne doit jamais primer sur celui qui est face à la caméra.
    """
    faces = _get_analyzer().get(image)
    if not faces:
        return None
    return max(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]))


def _get_analyzer() -> FaceAnalysis:
    if _analyzer is None:
        raise HTTPException(status_code=503, detail="Modèle non chargé")
    return _analyzer


def _normalise(vector: np.ndarray) -> np.ndarray:
    """
    L2 : on stocke des vecteurs unitaires pour que la similarité cosinus se
    réduise à un produit scalaire côté Java.
    """
    norm = float(np.linalg.norm(vector))
    return vector if norm == 0.0 else vector / norm


def _sain(valeur: float, defaut: float = 0.0) -> float:
    """
    Remplace NaN et ±Inf par une valeur finie.

    Un seul NaN dans la réponse et le sérialiseur JSON produit `NaN`, littéral
    absent de la norme : FastAPI lève, uvicorn renvoie un 500 sans en-tête de
    type, et le client Java échoue sur « content type [application/octet-stream] »
    au lieu d'un message lisible. Les NaN naissent facilement ici — moyenne d'une
    liste vide, division par une distance nulle entre deux points confondus.
    """
    try:
        nombre = float(valeur)
    except (TypeError, ValueError):
        return defaut
    return nombre if np.isfinite(nombre) else defaut


def _quality(face, image: np.ndarray) -> float:
    """
    Score composite dans [0, 1] : confiance du détecteur, taille relative du
    visage et netteté (variance du laplacien). Sert à refuser une inscription
    sur une image médiocre, pas à décider de l'identité.
    """
    x1, y1, x2, y2 = [int(v) for v in face.bbox]
    hauteur, largeur = image.shape[:2]

    surface = max(0, x2 - x1) * max(0, y2 - y1)
    ratio_taille = min(1.0, surface / float(hauteur * largeur) / 0.12)

    crop = image[max(0, y1):min(hauteur, y2), max(0, x1):min(largeur, x2)]
    if crop.size == 0:
        nettete = 0.0
    else:
        gris = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        nettete = min(1.0, float(cv2.Laplacian(gris, cv2.CV_64F).var()) / 180.0)

    confiance = min(1.0, float(face.det_score))
    return round(_sain(0.45 * confiance + 0.30 * ratio_taille + 0.25 * nettete), 4)


# ─── Mesures morphologiques ────────────────────────────────────────────────
#
# Indices des 106 points du modèle InsightFace, relevés empiriquement : les
# yeux occupent [33:43] et [87:97], le contour de bouche [52:72].

LEFT_EYE = (33, 43)
RIGHT_EYE = (87, 97)
MOUTH = (52, 72)


def _eye_openness(landmarks: np.ndarray, span: tuple) -> float:
    """
    Hauteur / largeur du nuage de points de l'œil.

    Volontairement insensible à l'ordre des points, contrairement à l'EAR
    canonique : l'ordre du modèle 106 points n'est pas documenté et un mauvais
    appariement produisait des valeurs aberrantes (> 1,0).
    """
    p = landmarks[span[0]:span[1]]
    largeur = float(p[:, 0].max() - p[:, 0].min())
    hauteur = float(p[:, 1].max() - p[:, 1].min())
    return _sain(hauteur / largeur, 1.0) if largeur > 0 else 1.0


def _nose_offset(face) -> Optional[float]:
    """
    Décalage horizontal du nez par rapport au milieu des yeux, rapporté à la
    distance inter-oculaire.

    Mesure de rotation indépendante de `pose` : les 5 points `kps` sont produits
    par le détecteur lui-même et toujours présents, là où `pose` dépend d'un
    modèle optionnel absent de certaines installations. Le nez saillant se
    décale nettement dès que la tête pivote, et la normalisation par l'écart
    des yeux rend la mesure insensible à la distance à la caméra.
    """
    kps = getattr(face, "kps", None)
    if kps is None or len(kps) < 3:
        return None

    points = np.asarray(kps, dtype=np.float32)
    oeil_gauche, oeil_droit, nez = points[0], points[1], points[2]

    inter_oculaire = float(np.linalg.norm(oeil_droit - oeil_gauche))
    if inter_oculaire <= 1e-6:
        return None

    milieu = (float(oeil_gauche[0]) + float(oeil_droit[0])) / 2.0
    return (float(nez[0]) - milieu) / inter_oculaire


def _metrics(face) -> dict:
    """Mesures brutes d'une trame. Les champs indisponibles valent `None`."""
    marks = getattr(face, "landmark_2d_106", None)
    pose = getattr(face, "pose", None)
    largeur_visage = float(face.bbox[2] - face.bbox[0])

    oeil: Optional[float] = None
    bouche: Optional[float] = None
    bouche_ratio: Optional[float] = None

    if marks is not None and len(marks) >= 106:
        marks = np.asarray(marks, dtype=np.float32)
        oeil = (_eye_openness(marks, LEFT_EYE) + _eye_openness(marks, RIGHT_EYE)) / 2.0
        contour = marks[MOUTH[0]:MOUTH[1]]
        largeur_bouche = float(contour[:, 0].max() - contour[:, 0].min())
        hauteur_bouche = float(contour[:, 1].max() - contour[:, 1].min())
        if largeur_visage > 0:
            bouche = largeur_bouche / largeur_visage
        # Rapport largeur/hauteur : un sourire étire la bouche ET l'aplatit, donc
        # ce rapport bouge bien plus que la largeur seule. Sur un vrai sourire la
        # largeur ne gagnait que 5 %, trop peu pour un seuil fiable.
        if hauteur_bouche > 0:
            bouche_ratio = _sain(largeur_bouche / hauteur_bouche, 0.0)

    return {
        "eye": oeil,
        "mouth": bouche,
        "mouth_ratio": bouche_ratio,
        "yaw": float(pose[1]) if pose is not None and len(pose) >= 2 else None,
        "nose": _nose_offset(face),
    }


# ─── Vivacité ──────────────────────────────────────────────────────────────

def _values(mesures: List[dict], cle: str) -> List[float]:
    """Valeurs non nulles d'une métrique sur une rafale."""
    return [m[cle] for m in mesures if m.get(cle) is not None]


def _check_blink(rafale: List[dict], repos: List[dict]) -> Tuple[bool, str]:
    """
    Le creux de la rafale contre le sommet du repos.

    On prend le maximum côté repos parce qu'une trame neutre peut elle-même
    tomber sur un clignement spontané : comparer au minimum ferait alors passer
    n'importe quoi.
    """
    ouvertures = _values(rafale, "eye")
    references = _values(repos, "eye")
    if not ouvertures or not references:
        return True, "Ouverture des yeux non mesurable"

    creux = min(ouvertures)
    ouvert = max(references)
    seuil = ouvert * BLINK_CLOSE_RATIO
    return creux < seuil, f"creux {creux:.3f} vs repos {ouvert:.3f} (seuil {seuil:.3f})"


def _check_smile(rafale: List[dict], repos: List[dict]) -> Tuple[bool, str]:
    """
    Sourire : élargissement de la bouche OU aplatissement de son contour.

    Les deux indices sont acceptés séparément parce que la largeur seule est peu
    discriminante — un sourire franc ne la gagne que de 5 %, à peine au-dessus du
    bruit de mesure. Le rapport largeur/hauteur, lui, bouge nettement : la bouche
    s'étire et s'aplatit en même temps.
    """
    preuves: List[str] = []
    detecte = False

    largeurs = _values(rafale, "mouth")
    references = _values(repos, "mouth")
    if largeurs and references:
        large = max(largeurs)
        # Médiane au repos : plus stable que la moyenne sur trois trames, dont une
        # peut attraper un début de sourire.
        neutre = float(np.median(references))
        seuil = neutre * SMILE_WIDEN_RATIO
        preuves.append(f"largeur {large:.3f} vs {neutre:.3f} (seuil {seuil:.3f})")
        detecte = detecte or large > seuil

    ratios = _values(rafale, "mouth_ratio")
    ratios_repos = _values(repos, "mouth_ratio")
    if ratios and ratios_repos:
        etire = max(ratios)
        neutre = float(np.median(ratios_repos))
        seuil = neutre * SMILE_RATIO_GAIN
        preuves.append(f"forme {etire:.2f} vs {neutre:.2f} (seuil {seuil:.2f})")
        detecte = detecte or etire > seuil

    if not preuves:
        return True, "Bouche non mesurable"
    return detecte, " · ".join(preuves)


def _check_turn(action: str, rafale: List[dict], repos: List[dict]) -> Tuple[bool, str]:
    """
    Rotation franche de la tête, mesurée sur le yaw ET sur le décalage du nez.

    Les deux mesures sont redondantes à dessein : `pose` manque sur certaines
    installations, et le décalage du nez sature sur les visages très ronds. On
    retient le mouvement le plus ample des deux.
    """
    attendu_negatif = action == "TURN_LEFT"
    preuves: List[str] = []
    detecte = False

    yaws = _values(rafale, "yaw")
    yaws_repos = _values(repos, "yaw")
    if yaws and yaws_repos:
        base = float(np.median(yaws_repos))
        ecarts = [y - base for y in yaws]
        # L'écart le plus ample de la rafale, signe compris.
        ecart = max(ecarts, key=abs)
        preuves.append(f"yaw {ecart:+.1f}° (seuil {YAW_DELTA_DEG}°)")
        if abs(ecart) >= YAW_DELTA_DEG:
            detecte = detecte or not TURN_DIRECTION_STRICT or (
                ecart < 0 if attendu_negatif else ecart > 0)

    nez = _values(rafale, "nose")
    nez_repos = _values(repos, "nose")
    if nez and nez_repos:
        base = float(np.median(nez_repos))
        ecarts = [n - base for n in nez]
        ecart = max(ecarts, key=abs)
        preuves.append(f"nez {ecart:+.3f} (seuil {NOSE_DELTA_RATIO})")
        if abs(ecart) >= NOSE_DELTA_RATIO:
            detecte = detecte or not TURN_DIRECTION_STRICT or (
                ecart < 0 if attendu_negatif else ecart > 0)

    if not preuves:
        return True, "Rotation non mesurable"
    return detecte, " · ".join(preuves)


def _check_slot(action: str, rafale: List[dict], repos: List[dict]) -> StepVerdict:
    """
    Un verdict par consigne, jugé sur toute la rafale et par écart au repos de
    la même personne. En l'absence de mesure exploitable on laisse passer : un
    faux négatif enferme dehors un utilisateur légitime, ce qui est pire ici que
    de s'appuyer sur les autres consignes de la séquence.
    """
    action = (action or "").upper()

    if action == "NEUTRAL":
        return StepVerdict(action=action, passed=True, reason="Trame de référence")
    if not rafale or not repos:
        return StepVerdict(action=action, passed=True, reason="Mesures indisponibles")

    if action == "BLINK":
        ok, raison = _check_blink(rafale, repos)
    elif action == "SMILE":
        ok, raison = _check_smile(rafale, repos)
    elif action in ("TURN_LEFT", "TURN_RIGHT"):
        ok, raison = _check_turn(action, rafale, repos)
    else:
        return StepVerdict(action=action, passed=False, reason="Consigne inconnue")

    return StepVerdict(action=action, passed=ok, reason=raison)


def _group_slots(frames: List[Frame]) -> List[Tuple[str, List[int]]]:
    """
    Regroupe les trames consécutives portant la même consigne.

    Le client envoie une rafale par consigne ; l'ordre des consignes distinctes
    reste celui de la cérémonie, ce que Java revérifie de son côté.
    """
    slots: List[Tuple[str, List[int]]] = []
    for index, frame in enumerate(frames):
        action = (frame.action or "").upper()
        if slots and slots[-1][0] == action:
            slots[-1][1].append(index)
        else:
            slots.append((action, [index]))
    return slots


# ─── Endpoints ─────────────────────────────────────────────────────────────

@app.get("/health")
def health() -> dict:
    return {"status": "UP" if _analyzer is not None else "LOADING", "model": MODEL_NAME}


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    image = _decode_image(request.image)
    face = _largest_face(image)

    if face is None:
        raise HTTPException(status_code=422, detail="Aucun visage détecté")
    if face.det_score < MIN_DET_SCORE:
        raise HTTPException(status_code=422, detail="Visage trop peu net ou trop petit")

    vecteur = _normalise(np.asarray(face.normed_embedding, dtype=np.float32))
    x1, y1, x2, y2 = [int(v) for v in face.bbox]

    return EmbedResponse(
        embedding=[_sain(v) for v in vecteur],
        quality=_quality(face, image),
        detScore=round(_sain(float(face.det_score)), 4),
        box=BoundingBox(x=x1, y=y1, width=x2 - x1, height=y2 - y1),
    )


@app.post("/verify-liveness", response_model=LivenessResponse)
def verify_liveness(request: LivenessRequest) -> LivenessResponse:
    """
    Vérifie qu'une séquence de rafales exécute bien les consignes demandées, et
    que c'est la même personne du début à la fin.

    L'embedding renvoyé est la moyenne des trames DE FACE : moyenner réduit le
    bruit d'une trame isolée, mais inclure des vues de profil décalerait le
    gabarit et ferait chuter la similarité aux connexions suivantes.
    """
    if not request.frames:
        raise HTTPException(status_code=400, detail="Aucune trame fournie")
    if len(request.frames) > MAX_FRAMES:
        raise HTTPException(status_code=413, detail=f"Trop de trames (max {MAX_FRAMES})")

    analyses: Dict[int, dict] = {}
    embeddings: Dict[int, np.ndarray] = {}
    qualites: Dict[int, float] = {}
    exploitables = 0

    for index, frame in enumerate(request.frames):
        image = _decode_image(frame.image)
        face = _largest_face(image)
        if face is None or face.det_score < MIN_DET_SCORE:
            continue

        analyses[index] = _metrics(face)
        embeddings[index] = _normalise(np.asarray(face.normed_embedding, dtype=np.float32))
        qualites[index] = _quality(face, image)
        exploitables += 1

    if not exploitables:
        return LivenessResponse(
            live=False, identityConsistent=False,
            steps=[StepVerdict(action="NEUTRAL", passed=False,
                               reason="Aucun visage détecté dans la séquence")],
            reason="Aucun visage exploitable dans la séquence")

    slots = _group_slots(request.frames)

    # Repos : la rafale NEUTRAL, à défaut la première rafale exploitable. C'est
    # elle qui calibre tous les seuils sur la morphologie de cette personne.
    repos = next(
        ([analyses[i] for i in indices if i in analyses]
         for action, indices in slots
         if action == "NEUTRAL" and any(i in analyses for i in indices)),
        None)
    if repos is None:
        repos = next(
            ([analyses[i] for i in indices if i in analyses]
             for _, indices in slots if any(i in analyses for i in indices)),
            [])

    verdicts: List[StepVerdict] = []
    for action, indices in slots:
        mesurees = [analyses[i] for i in indices if i in analyses]
        if not mesurees:
            verdicts.append(StepVerdict(action=action, passed=False,
                                        reason="Aucun visage détecté sur cette consigne"))
            continue
        verdicts.append(_check_slot(action, mesurees, repos))

    # Trames de face : seules elles entrent dans le gabarit. Le repli sur la
    # totalité évite de renvoyer un embedding vide si l'utilisateur a gardé la
    # tête tournée pendant toute la cérémonie.
    origine = _base_nose(analyses, repos)
    frontales = [
        i for i in embeddings
        if analyses[i].get("nose") is None
        or abs(analyses[i]["nose"] - origine) <= FRONTAL_NOSE_LIMIT
    ]
    retenues = frontales or list(embeddings.keys())

    reference = embeddings[retenues[0]]
    similarites = [float(np.dot(reference, embeddings[i])) for i in retenues[1:]]
    # Médiane plutôt que « toutes » : une trame floue en cours de mouvement fait
    # chuter la similarité sans qu'il y ait eu changement de personne.
    coherent = (not similarites
                or float(np.median(similarites)) >= IDENTITY_CONSISTENCY_THRESHOLD)

    consignes = [v for v in verdicts if v.action != "NEUTRAL"]
    consignes_ok = bool(consignes) and all(v.passed for v in consignes)
    vivant = consignes_ok and coherent

    moyenne = _normalise(np.mean(np.stack([embeddings[i] for i in retenues]), axis=0))

    if not coherent:
        raison = "Le visage change au cours de la séquence"
    elif not consignes_ok:
        echouees = [v.action for v in consignes if not v.passed]
        raison = "Consignes non exécutées : " + ", ".join(echouees)
    else:
        raison = "OK"

    logger.info("Vivacité : live=%s cohérent=%s — %s", vivant, coherent,
                " | ".join(f"{v.action}={'ok' if v.passed else 'ko'} ({v.reason})"
                           for v in verdicts))

    retenues_qualites = [qualites[i] for i in retenues if i in qualites]

    return LivenessResponse(
        live=vivant,
        identityConsistent=coherent,
        steps=verdicts,
        embedding=[_sain(v) for v in moyenne],
        quality=round(_sain(float(np.mean(retenues_qualites))), 4)
                if retenues_qualites else 0.0,
        reason=raison,
    )


def _base_nose(analyses: Dict[int, dict], repos: List[dict]) -> float:
    """Décalage du nez au repos : origine de l'axe de rotation pour cette personne."""
    valeurs = [m["nose"] for m in repos if m.get("nose") is not None]
    if not valeurs:
        valeurs = [m["nose"] for m in analyses.values() if m.get("nose") is not None]
    return float(np.median(valeurs)) if valeurs else 0.0


@app.exception_handler(HTTPException)
async def http_exception_handler(_, exc: HTTPException):
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})


@app.exception_handler(Exception)
async def unhandled_exception_handler(_, exc: Exception):
    """
    Toute erreur imprévue repart quand même en JSON.

    Sans ce filet, uvicorn répond un 500 au corps vide et sans type de contenu ;
    le client Java échoue alors sur « content type [application/octet-stream] »
    et l'utilisateur voit un nom de classe Java au lieu d'un message utile.
    """
    logger.exception("Erreur non gérée : %s", exc)
    return JSONResponse(
        status_code=500,
        content={"detail": "Erreur interne du service de reconnaissance faciale"},
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host=os.getenv("FACE_HOST", "127.0.0.1"),
        port=int(os.getenv("FACE_PORT", "8000")),
        reload=False,
    )
