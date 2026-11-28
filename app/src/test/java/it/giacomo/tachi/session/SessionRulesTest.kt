package it.giacomo.tachi.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRulesTest {
    private val start = SessionState()

    @Test
    fun siAttivaQuandoLAutoSiCollega() {
        val s = SessionRules.carConnected(start, "Auto")
        assertEquals(Phase.ACTIVE, s.phase)
        assertEquals("Auto", s.carName)
    }

    @Test
    fun loScollegamentoFaPartireIlContoDa60() {
        val s = SessionRules.noCar(SessionRules.carConnected(start, "Auto"))
        assertEquals(Phase.LEAVING, s.phase)
        assertEquals(60, s.secondsLeft)
    }

    @Test
    fun dopo60SecondiSiChiude() {
        var s = SessionRules.noCar(SessionRules.carConnected(start, "Auto"))
        repeat(59) { s = SessionRules.tick(s) }
        assertEquals(Phase.LEAVING, s.phase)
        assertEquals(1, s.secondsLeft)
        s = SessionRules.tick(s)
        assertEquals(Phase.CLOSING, s.phase)
    }

    @Test
    fun seLAutoSiRicollegaIlContoSiAnnulla() {
        var s = SessionRules.noCar(SessionRules.carConnected(start, "Auto"))
        repeat(10) { s = SessionRules.tick(s) }
        s = SessionRules.carConnected(s, "Auto")
        assertEquals(Phase.ACTIVE, s.phase)
        assertEquals(0, s.secondsLeft)
    }

    @Test
    fun restoQuiTornaInAttesaSenzaChiudere() {
        var s = SessionRules.noCar(SessionRules.carConnected(start, "Auto"))
        s = SessionRules.stayHere(s)
        assertEquals(Phase.WAITING, s.phase)
        repeat(120) { s = SessionRules.tick(s) }
        assertEquals(Phase.WAITING, s.phase)
    }

    @Test
    fun senzaAutoNonParteNessunConto() {
        val s = SessionRules.noCar(start)
        assertEquals(Phase.WAITING, s.phase)
    }

    @Test
    fun laProvaSenzaAutoNonSiChiudeDaSola() {
        var s = SessionRules.startTest(start)
        assertEquals(Phase.TEST, s.phase)
        s = SessionRules.noCar(s)
        repeat(120) { s = SessionRules.tick(s) }
        assertEquals(Phase.TEST, s.phase)
    }

    @Test
    fun duranteLaProvaSeArrivaLAutoDiventaVera() {
        var s = SessionRules.startTest(start)
        s = SessionRules.carConnected(s, "Auto")
        assertEquals(Phase.ACTIVE, s.phase)
    }

    @Test
    fun unaVoltaInChiusuraNonTornaIndietro() {
        var s = SessionRules.noCar(SessionRules.carConnected(start, "Auto"))
        repeat(60) { s = SessionRules.tick(s) }
        s = SessionRules.carConnected(s, "Auto")
        assertEquals(Phase.CLOSING, s.phase)
    }
}
