package com.example.pokemonsavemanager

data class Gen3MetLocationMetadata(val originGame: Int, val location: Int)
data class Gen3MetLocationChoice(val id: Int, val name: String)

/** Edit ordinary met locations using the Pokémon's origin title, including traded Pokémon. */
object Gen3MetLocationEditing {
    // Misc +1 is metLocation. Misc +2 bits 7..10 are the unchanged origin version.
    // Named IDs follow the generated zero-based MAPSEC enum, not the current save's title.
    // https://github.com/pret/pokeemerald/blob/master/include/pokemon.h
    // https://github.com/pret/pokeemerald/blob/master/src/data/region_map/region_map_sections.json
    // https://github.com/pret/pokeruby/blob/master/src/data/region_map/region_map_sections.json
    // https://github.com/pret/pokefirered/blob/master/src/data/region_map/region_map_sections.json
    // https://github.com/pret/pokeemerald/blob/master/src/data/region_map/region_map_sections.constants.json.txt
    private val names = listOf(
        "LITTLEROOT TOWN", // 0
        "OLDALE TOWN", // 1
        "DEWFORD TOWN", // 2
        "LAVARIDGE TOWN", // 3
        "FALLARBOR TOWN", // 4
        "VERDANTURF TOWN", // 5
        "PACIFIDLOG TOWN", // 6
        "PETALBURG CITY", // 7
        "SLATEPORT CITY", // 8
        "MAUVILLE CITY", // 9
        "RUSTBORO CITY", // 10
        "FORTREE CITY", // 11
        "LILYCOVE CITY", // 12
        "MOSSDEEP CITY", // 13
        "SOOTOPOLIS CITY", // 14
        "EVER GRANDE CITY", // 15
        "ROUTE 101", // 16
        "ROUTE 102", // 17
        "ROUTE 103", // 18
        "ROUTE 104", // 19
        "ROUTE 105", // 20
        "ROUTE 106", // 21
        "ROUTE 107", // 22
        "ROUTE 108", // 23
        "ROUTE 109", // 24
        "ROUTE 110", // 25
        "ROUTE 111", // 26
        "ROUTE 112", // 27
        "ROUTE 113", // 28
        "ROUTE 114", // 29
        "ROUTE 115", // 30
        "ROUTE 116", // 31
        "ROUTE 117", // 32
        "ROUTE 118", // 33
        "ROUTE 119", // 34
        "ROUTE 120", // 35
        "ROUTE 121", // 36
        "ROUTE 122", // 37
        "ROUTE 123", // 38
        "ROUTE 124", // 39
        "ROUTE 125", // 40
        "ROUTE 126", // 41
        "ROUTE 127", // 42
        "ROUTE 128", // 43
        "ROUTE 129", // 44
        "ROUTE 130", // 45
        "ROUTE 131", // 46
        "ROUTE 132", // 47
        "ROUTE 133", // 48
        "ROUTE 134", // 49
        "UNDERWATER", // 50
        "UNDERWATER", // 51
        "UNDERWATER", // 52
        "UNDERWATER", // 53
        "UNDERWATER", // 54
        "GRANITE CAVE", // 55
        "MT. CHIMNEY", // 56
        "SAFARI ZONE", // 57
        "BATTLE FRONTIER", // 58
        "PETALBURG WOODS", // 59
        "RUSTURF TUNNEL", // 60
        "ABANDONED SHIP", // 61
        "NEW MAUVILLE", // 62
        "METEOR FALLS", // 63
        "METEOR FALLS", // 64
        "MT. PYRE", // 65
        "AQUA HIDEOUT", // 66
        "SHOAL CAVE", // 67
        "SEAFLOOR CAVERN", // 68
        "UNDERWATER", // 69
        "VICTORY ROAD", // 70
        "MIRAGE ISLAND", // 71
        "CAVE OF ORIGIN", // 72
        "SOUTHERN ISLAND", // 73
        "FIERY PATH", // 74
        "FIERY PATH", // 75
        "JAGGED PASS", // 76
        "JAGGED PASS", // 77
        "SEALED CHAMBER", // 78
        "UNDERWATER", // 79
        "SCORCHED SLAB", // 80
        "ISLAND CAVE", // 81
        "DESERT RUINS", // 82
        "ANCIENT TOMB", // 83
        "INSIDE OF TRUCK", // 84
        "SKY PILLAR", // 85
        "SECRET BASE", // 86
        "", // 87
        "PALLET TOWN", // 88
        "VIRIDIAN CITY", // 89
        "PEWTER CITY", // 90
        "CERULEAN CITY", // 91
        "LAVENDER TOWN", // 92
        "VERMILION CITY", // 93
        "CELADON CITY", // 94
        "FUCHSIA CITY", // 95
        "CINNABAR ISLAND", // 96
        "INDIGO PLATEAU", // 97
        "SAFFRON CITY", // 98
        "ROUTE 4", // 99
        "ROUTE 10", // 100
        "ROUTE 1", // 101
        "ROUTE 2", // 102
        "ROUTE 3", // 103
        "ROUTE 4", // 104
        "ROUTE 5", // 105
        "ROUTE 6", // 106
        "ROUTE 7", // 107
        "ROUTE 8", // 108
        "ROUTE 9", // 109
        "ROUTE 10", // 110
        "ROUTE 11", // 111
        "ROUTE 12", // 112
        "ROUTE 13", // 113
        "ROUTE 14", // 114
        "ROUTE 15", // 115
        "ROUTE 16", // 116
        "ROUTE 17", // 117
        "ROUTE 18", // 118
        "ROUTE 19", // 119
        "ROUTE 20", // 120
        "ROUTE 21", // 121
        "ROUTE 22", // 122
        "ROUTE 23", // 123
        "ROUTE 24", // 124
        "ROUTE 25", // 125
        "VIRIDIAN FOREST", // 126
        "MT. MOON", // 127
        "S.S. ANNE", // 128
        "UNDERGROUND PATH", // 129
        "UNDERGROUND PATH", // 130
        "DIGLETT'S CAVE", // 131
        "VICTORY ROAD", // 132
        "ROCKET HIDEOUT", // 133
        "SILPH CO.", // 134
        "POKéMON MANSION", // 135
        "SAFARI ZONE", // 136
        "POKéMON LEAGUE", // 137
        "ROCK TUNNEL", // 138
        "SEAFOAM ISLANDS", // 139
        "POKéMON TOWER", // 140
        "CERULEAN CAVE", // 141
        "POWER PLANT", // 142
        "ONE ISLAND", // 143
        "TWO ISLAND", // 144
        "THREE ISLAND", // 145
        "FOUR ISLAND", // 146
        "FIVE ISLAND", // 147
        "SEVEN ISLAND", // 148
        "SIX ISLAND", // 149
        "KINDLE ROAD", // 150
        "TREASURE BEACH", // 151
        "CAPE BRINK", // 152
        "BOND BRIDGE", // 153
        "THREE ISLE PORT", // 154
        "SEVII ISLE 6", // 155
        "SEVII ISLE 7", // 156
        "SEVII ISLE 8", // 157
        "SEVII ISLE 9", // 158
        "RESORT GORGEOUS", // 159
        "WATER LABYRINTH", // 160
        "FIVE ISLE MEADOW", // 161
        "MEMORIAL PILLAR", // 162
        "OUTCAST ISLAND", // 163
        "GREEN PATH", // 164
        "WATER PATH", // 165
        "RUIN VALLEY", // 166
        "TRAINER TOWER", // 167
        "CANYON ENTRANCE", // 168
        "SEVAULT CANYON", // 169
        "TANOBY RUINS", // 170
        "SEVII ISLE 22", // 171
        "SEVII ISLE 23", // 172
        "SEVII ISLE 24", // 173
        "NAVEL ROCK", // 174
        "MT. EMBER", // 175
        "BERRY FOREST", // 176
        "ICEFALL CAVE", // 177
        "ROCKET WAREHOUSE", // 178
        "TRAINER TOWER", // 179
        "DOTTED HOLE", // 180
        "LOST CAVE", // 181
        "PATTERN BUSH", // 182
        "ALTERING CAVE", // 183
        "TANOBY CHAMBERS", // 184
        "THREE ISLE PATH", // 185
        "TANOBY KEY", // 186
        "BIRTH ISLAND", // 187
        "MONEAN CHAMBER", // 188
        "LIPTOO CHAMBER", // 189
        "WEEPTH CHAMBER", // 190
        "DILFORD CHAMBER", // 191
        "SCUFIB CHAMBER", // 192
        "RIXY CHAMBER", // 193
        "VIAPOIS CHAMBER", // 194
        "EMBER SPA", // 195
        "CELADON DEPT.", // 196
        "AQUA HIDEOUT", // 197
        "MAGMA HIDEOUT", // 198
        "MIRAGE TOWER", // 199
        "BIRTH ISLAND", // 200
        "FARAWAY ISLAND", // 201
        "ARTISAN CAVE", // 202
        "MARINE CAVE", // 203
        "UNDERWATER", // 204
        "TERRA CAVE", // 205
        "UNDERWATER", // 206
        "UNDERWATER", // 207
        "UNDERWATER", // 208
        "DESERT UNDERPASS", // 209
        "ALTERING CAVE", // 210
        "NAVEL ROCK", // 211
        "TRAINER HILL" // 212
    )

    fun choices(originGame: Int): List<Gen3MetLocationChoice> {
        val ids = when (originGame) {
            1, 2 -> (0..86).toList() // Sapphire / Ruby; 87 is a dynamic, unnamed map section.
            3 -> (0..86).toList() + (197..212).toList() // Emerald's additional locations.
            4, 5 -> (88..196).toList() // FireRed / LeafGreen (Kanto and Sevii).
            else -> throw IllegalArgumentException("Met-location editing supports Ruby, Sapphire, Emerald, FireRed and LeafGreen origins.")
        }
        return ids.map { id ->
            val name = when {
                id == 58 && originGame in 1..2 -> "BATTLE TOWER"
                id == 66 && originGame == 2 -> "MAGMA HIDEOUT"
                else -> names[id]
            }
            Gen3MetLocationChoice(id, name)
        }
    }

    fun read(record: ByteArray): Gen3MetLocationMetadata {
        Gen3CaptureEditing.read(record) // Complete record, checksum, Egg and capture-field validation.
        val data = Gen3Pk3Codec.decode(record.copyOfRange(0, 80)).canonicalSection
        val word = (data[38].toInt() and 255) or ((data[39].toInt() and 255) shl 8)
        val origin = (word ushr 7) and 15
        val location = data[37].toInt() and 255
        require(choices(origin).any { it.id == location } || location in 253..255) {
            "The source met location is unsupported for its origin title."
        }
        return Gen3MetLocationMetadata(origin, location)
    }

    fun replace(record: ByteArray, location: Int): ByteArray {
        val before = read(record)
        val options = choices(before.originGame)
        require(options.any { it.id == before.location }) {
            "Special event, Egg-gift and in-game-trade locations are read-only in this editor."
        }
        require(options.any { it.id == location }) { "Choose a named location from this Pokémon's origin title." }
        if (before.location == location) return record.copyOf()
        val decoded = Gen3Pk3Codec.decode(record.copyOfRange(0, 80))
        val canonical = decoded.canonicalSection.copyOf()
        canonical[37] = location.toByte()
        val stored = Gen3Pk3Codec.encode(decoded.header, decoded.personality,
            decoded.trainerId, decoded.secretId, canonical)
        val result = record.copyOf().also { stored.copyInto(it) }
        val after = Gen3Pk3Codec.decode(result.copyOfRange(0, 80))
        require(after.canonicalSection.contentEquals(canonical) &&
            canonical.indices.all { it == 37 || canonical[it] == decoded.canonicalSection[it] } &&
            (0 until 32).all { it in 28..29 || result[it] == record[it] } &&
            (80 until record.size).all { result[it] == record[it] } &&
            read(result) == Gen3MetLocationMetadata(before.originGame, location)) {
            "The PK3 met-location edit failed exact field or integrity verification."
        }
        return result
    }
}
