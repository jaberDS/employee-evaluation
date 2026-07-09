package com.atb.employeeevaluation.entity;

import com.atb.employeeevaluation.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String matricule;

    @Column(nullable = false, length = 50)
    private String nom;

    @Column(nullable = false, length = 50)
    private String prenom;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "n1_id")
    private Employe n1;

    @ManyToOne
    @JoinColumn(name = "n2_id")
    private Employe n2;

    @Column(nullable = false)
    @Builder.Default  // ✅ Ajouté pour supprimer le warning
    private Boolean actif = true;



    @OneToMany(mappedBy = "employe", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FicheEvaluation> fiches = new ArrayList<>();
    // À ajouter plus tard quand on créera FicheEvaluation
    // @OneToMany(mappedBy = "employe")
    // private List<FicheEvaluation> fiches = new ArrayList<>();
}