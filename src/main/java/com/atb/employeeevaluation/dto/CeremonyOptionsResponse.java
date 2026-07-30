package com.atb.employeeevaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Options de cérémonie à transmettre telles quelles à navigator.credentials. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CeremonyOptionsResponse {

    private String ceremonyId;

    /** JSON WebAuthn-L3 produit par la bibliothèque Yubico, passé brut au navigateur. */
    private String optionsJSON;
}
