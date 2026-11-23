# Tachimetro

Cruscotto per l'auto su telefono e tablet Android, pensato per essere usato soprattutto a voce. Si attiva con «Ehi Tachi».

## Installazione

1. Apri la pagina **Releases** di questo repository dal telefono o dal tablet.
2. Scarica il file `Tachimetro-….apk` più recente.
3. Aprilo e consenti l'installazione da questa fonte quando Android lo chiede.
4. Al primo avvio concedi microfono, posizione, contatti, calendario, telefono e Bluetooth.
5. Nelle Impostazioni di Tachimetro, alla voce Permessi, attiva anche la lettura delle notifiche (serve per messaggi e musica) e l'apertura automatica.
6. Sempre nelle Impostazioni, alla voce Auto, scegli il Bluetooth della tua auto e inserisci gli indirizzi di casa e lavoro.

Gli aggiornamenti si installano sopra la versione precedente senza perdere le impostazioni.

## Cosa si può dire

| Per | Di' |
|---|---|
| Navigare | «portami a casa», «portami in via Roma 10 a Napoli», «portami a casa con Waze», «torna a Google Maps», «ferma la navigazione» |
| Musica | «metti Vasco su Spotify», «metti la playlist viaggio», «pausa», «riprendi», «avanti», «alza il volume» |
| Radio | «metti Radio Deejay», «metti radio RTL», «radio successiva» |
| Telefono | «chiama Luca», «leggi i messaggi», «rispondi», «entra nella riunione» |
| App | «apri WhatsApp», «cerca Stranger Things su Netflix», «aggiungi Calendario alla barra» |
| Auto | «dov'è la macchina?», «trova benzina qui vicino», «portami al primo», «che tempo fa» |
| Modalità | «modalità passeggero», «modalità guidatore», «silenzia avvisi» |
| Altro | qualsiasi domanda va a Gemini |

## Cosa fa questa versione

- Cruscotto con mappa di Google e pannello regolabile, tema giorno e notte automatico, barra delle app personalizzabile.
- Parola chiave riconosciuta sul telefono, senza internet. I comandi vengono capiti dal servizio vocale di Google del telefono; se manca, dal modello vocale interno.
- Musica di Spotify, YouTube Music e Amazon Music; radio nazionali e regionali in streaming.
- Messaggi di WhatsApp, SMS e Teams letti ad alta voce, con risposta dettata e riletta prima dell'invio.
- Velocità, limite e avviso discreto durante la navigazione; autovelox fissi da OpenStreetMap.
- Meteo e temperatura esterna, distributori più economici con i prezzi ufficiali del Ministero, parcheggio salvato quando ti scolleghi dal Bluetooth dell'auto, promemoria per le strisce blu.
- Avvisi per batteria scarica e telefono troppo caldo.
- Modalità passeggero con DAZN, Netflix, Prime Video, HBO Max, NOW e RaiPlay.

In arrivo: elenco ufficiale MIT degli autovelox e controlli mobili della Polizia Stradale, posizione e percorso condivisi tra telefono e tablet tramite hotspot.

## Mappa di Google

La mappa integrata usa una chiave di Google Maps con Navigation SDK attivo, limitata al pacchetto `it.giacomo.tachimetro` e all'impronta SHA-1 della firma. Senza chiave l'app funziona lo stesso e apre la navigazione nell'app Google Maps.

## Privacy

- La parola chiave viene riconosciuta sul telefono, senza internet.
- Le notifiche vengono lette ad alta voce e non vengono mai salvate.
- Gemini riceve solo il testo delle domande generali, e solo se hai inserito la chiave.
- Meteo, prezzi dei carburanti, autovelox e radio ricevono solo la zona in cui ti trovi.
- Nessun dato viene inviato a server di Tachimetro: non ce ne sono.

## Segreti

La chiave di firma e la chiave di Google Maps non sono nel repository: stanno nei Secrets di GitHub (Settings, Secrets and variables, Actions).

| Segreto | Contenuto |
|---|---|
| `KEYSTORE_BASE64` | la chiave di firma convertita in testo |
| `KEYSTORE_PASSWORD` | password della chiave di firma |
| `KEY_ALIAS` | `tachimetro` |
| `KEY_PASSWORD` | password dell'alias |
| `MAPS_API_KEY` | chiave di Google Maps |

La compilazione si ferma con un messaggio chiaro se un segreto manca o è sbagliato, e controlla che la firma dell'APK sia sempre la stessa (SHA-1 `75:E5:05:09:3B:47:A8:09:8E:E1:B0:9A:E6:E6:B4:84:80:19:25:DB`): se cambiasse, Android non accetterebbe più gli aggiornamenti. Conserva una copia della chiave di firma in un posto sicuro fuori da GitHub.
