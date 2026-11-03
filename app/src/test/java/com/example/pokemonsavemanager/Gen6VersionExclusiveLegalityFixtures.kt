package com.example.pokemonsavemanager

internal val GEN6_TEST_EXCLUSIVE_VERIFIER =
    Gen6VersionExclusiveLegalityVerifier { destination, record, evidence ->
        require(evidence.startsWith("fixture:"))
        Gen6VersionExclusiveLegalityResult(
            destinationGame = destination,
            species = record.species,
            encounterLegal = true,
            movesLegal = true,
            abilityLegal = true,
            formLegal = true,
            originMetadataLegal = true,
            note = "Synthetic regression fixture"
        )
    }
