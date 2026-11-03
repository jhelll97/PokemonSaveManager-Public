package com.example.pokemonsavemanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen6DocumentIdentityTest {
    @Test
    fun directAndTreeScopedUrisForSameDocumentMatch() {
        val direct =
            "content://com.android.externalstorage.documents/" +
                "document/primary%3APokemon%2Fmain.sav"
        val treeScoped =
            "content://COM.ANDROID.EXTERNALSTORAGE.DOCUMENTS/" +
                "tree/primary%3APokemon/document/" +
                "primary%3aPokemon%2fmain.sav?picker=tree"

        assertTrue(Gen6DocumentIdentity.sameDocument(direct, treeScoped))
    }

    @Test
    fun differentProviderOrDocumentIdDoesNotMatch() {
        val source =
            "content://provider.one/document/primary%3APokemon%2Fmain.sav"
        val differentProvider =
            "content://provider.two/document/primary%3APokemon%2Fmain.sav"
        val differentDocument =
            "content://provider.one/document/primary%3APokemon%2Fother.sav"

        assertFalse(
            Gen6DocumentIdentity.sameDocument(source, differentProvider)
        )
        assertFalse(
            Gen6DocumentIdentity.sameDocument(source, differentDocument)
        )
    }

    @Test
    fun plusIsNotDecodedAsSpaceAndMalformedUrisFailClosed() {
        val plus = "content://provider/document/folder%3Afile+name.sav"
        val space = "content://provider/document/folder%3Afile%20name.sav"
        val malformedA = "content://provider/document/folder%3"
        val malformedB = "content://provider/document/folder%ZZ"

        assertFalse(Gen6DocumentIdentity.sameDocument(plus, space))
        assertFalse(
            Gen6DocumentIdentity.sameDocument(malformedA, malformedB)
        )
        assertTrue(
            Gen6DocumentIdentity.sameDocument(malformedA, malformedA)
        )
    }

    @Test
    fun nonDocumentUrisMatchOnlyWhenTheirStringsAreIdentical() {
        val first = "file:///storage/emulated/0/Pokemon/main.sav"
        val second = "file:///storage/emulated/0/Pokemon/other.sav"

        assertTrue(Gen6DocumentIdentity.sameDocument(first, first))
        assertFalse(Gen6DocumentIdentity.sameDocument(first, second))
        assertFalse(Gen6DocumentIdentity.sameDocument(first, null))
    }
}
