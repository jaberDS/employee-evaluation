package com.atb.employeeevaluation.controller;

import com.atb.employeeevaluation.dto.AssistantApercuDTO;
import com.atb.employeeevaluation.dto.AssistantRequest;
import com.atb.employeeevaluation.dto.AssistantResponse;
import com.atb.employeeevaluation.service.AssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Assistant conversationnel, ouvert à tous les rôles.
 *
 * Aucune règle n'est ajoutée à SecurityConfig : la clause finale
 * `.anyRequest().authenticated()` couvre déjà ces routes. Le rôle est ensuite
 * lu depuis le jeton par le service, qui restreint les destinations en
 * conséquence.
 */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /** Permet au client de masquer l'assistant si le serveur n'a pas de clé. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("disponible", assistantService.estDisponible()));
    }

    /**
     * Ce qui attend l'utilisateur, à l'ouverture du panneau.
     *
     * Calculé entièrement côté serveur : aucun appel au modèle, donc pas de
     * consommation de quota pour un simple coup d'œil.
     */
    @GetMapping("/apercu")
    public ResponseEntity<AssistantApercuDTO> apercu() {
        return ResponseEntity.ok(assistantService.apercu());
    }

    @PostMapping("/ask")
    public ResponseEntity<AssistantResponse> ask(@Valid @RequestBody AssistantRequest request) {
        return ResponseEntity.ok(assistantService.repondre(request.getQuestion()));
    }

    /** Vider le fil côté client efface aussi la conversation retenue ici. */
    @DeleteMapping("/conversation")
    public ResponseEntity<Void> oublier() {
        assistantService.oublier();
        return ResponseEntity.noContent().build();
    }
}
