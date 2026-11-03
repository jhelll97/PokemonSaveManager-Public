package com.example.pokemonsavemanager

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BdspContestReadOnlyTest {
    private val layouts = listOf(0xE9828 to 0x25, 0xEDC20 to 0x2C, 0xEED8C to 0x32, 0xEF0A4 to 0x34)
    private val base=0x79C08
    private fun put(b:ByteArray,o:Int,v:Long){repeat(4){b[o+it]=(v ushr (8*it)).toByte()}}
    private fun seal(b:ByteArray){b.fill(0,0xE9818,0xE9828);MessageDigest.getInstance("MD5").digest(b).copyInto(b,0xE9818)}
    private fun fixture(layout:Int=0,title:Int=0)=ByteArray(layouts[layout].first).also{b->put(b,0,layouts[layout].second.toLong());put(b,0x79B78,2);b[0x79BDF]=title.toByte();repeat(0x720){b[base+it]=(it*17).toByte()};put(b,base+0x71C,0xFFFFFFFFL);b[base-1]=0xA5.toByte();b[base+0x720]=0x5A;seal(b)}
    private fun session(b:ByteArray)=SwitchReadSession(SwitchReadSnapshot.capture(SwitchGameFamily.LEGENDS_ZA,b))
    @Test fun bothTitlesAllRevisionsRetainRankPhotosAndBoundaries(){for(l in layouts.indices)for(t in 0..1){val b=fixture(l,t);val before=b.copyOf();val r=BdspContestReadOnly.read(b);assertEquals(t,r.version.romCode);assertEquals(layouts[l].second.toLong(),r.revision.code);assertEquals(0xFFFFFFFFL,r.rankPoints);assertEquals(5,r.photos.size);assertEquals(0x720,r.rawBytes.size);for(i in 0 until 5){assertEquals(i,r.photo(i).index);assertEquals(before.copyOfRange(base+i*0x16C,base+(i+1)*0x16C).map{it.toInt()and 255},r.photo(i).rawBytes)};assertEquals(before.copyOfRange(base,base+0x720).map{it.toInt()and 255},r.rawBytes);assertEquals(0xA5,b[base-1].toInt()and 255);assertEquals(0x5A,b[base+0x720].toInt()and 255);assertArrayEquals(before,b);for(x in listOf(-1,5,Int.MIN_VALUE,Int.MAX_VALUE))assertThrows(IllegalArgumentException::class.java){r.photo(x)}}}
    @Test fun rankWordPreservesAllUnsignedBitsAndPhotosRemainRaw(){for(v in listOf(0L,1L,0x7FFFFFFFL,0x80000000L,0xFFFFFFFFL)+ (0 until 32).map{1L shl it}){val b=fixture();put(b,base+0x71C,v);seal(b);val before=b.copyOf();assertEquals(v,BdspContestReadOnly.read(b).rankPoints);assertArrayEquals(before,b)}}
    @Test fun integrityIdentityAndSizePrecedeRead(){for(l in layouts.indices){val b=fixture(l);for(o in setOf(0,4,base-1,base,base+0x71B,base+0x71C,base+0x71F,base+0x720,0xE9818,b.lastIndex)){val bad=b.copyOf();bad[o]=(bad[o].toInt()xor 1).toByte();val before=bad.copyOf();assertThrows(IllegalArgumentException::class.java){BdspContestReadOnly.read(bad)};assertNull(session(bad).bdspContest);assertArrayEquals(before,bad)};val title=b.copyOf();title[0x79BDF]=2;seal(title);val rev=b.copyOf();put(rev,0,0x35);seal(rev);for(bad in listOf(title,rev,b.copyOf(b.size-1),b.copyOf(b.size+1)))assertThrows(IllegalArgumentException::class.java){BdspContestReadOnly.read(bad)}};for(bad in listOf(ByteArray(0),ByteArray(1),ByteArray(0xEF0A4)))assertThrows(IllegalArgumentException::class.java){BdspContestReadOnly.read(bad)}}
    @Test fun strictGatesRemainIndependent(){val b=fixture().also{put(it,0x79B78,6);put(it,0x7A328,4);it[0x14098+6*344]=7;seal(it)};val s=session(b);assertTrue(s.isFormatVerified);assertNotNull(s.bdspContest);assertNull(s.bdspTrainer);assertNull(s.bdspStorage);assertNull(s.bdspPokedex);assertArrayEquals(b,b.copyOf())}
    @Test fun detachedImmutablePhotoRecordsPreserveSourceAndWriteLocks(){val b=fixture();val before=b.copyOf();val s=session(b);val r=s.bdspContest!!;b.fill(0);s.snapshot.bytesCopy().fill(0);assertArrayEquals(before,s.snapshot.bytesCopy());assertEquals(5,r.photos.size);assertThrows(UnsupportedOperationException::class.java){(r.photos as MutableList<*>).clear()};assertThrows(UnsupportedOperationException::class.java){(r.rawBytes as MutableList<*>).clear()};assertTrue(s.isReadOnly);assertFalse(s.profile.writesEnabled)}
    @Test fun otherFamiliesDoNotExposeContest(){assertNull(session(byteArrayOf(1)).bdspContest)}
}

