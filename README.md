# Tachimetro

Cruscotto per l'auto su telefono e tablet Android, pensato per essere usato soprattutto a voce. Si attiva con «Ehi Tachi».

## Installazione

1. Apri la pagina **Releases** di questo repository dal telefono o dal tablet.
2. Scarica il file `Tachimetro-….apk` più recente.
3. Aprilo e consenti l'installazione da questa fonte quando Android lo chiede.
4. Al primo avvio la configurazione guidata ti accompagna: permessi, lettura delle notifiche, Non disturbare, batteria, indirizzi di casa e lavoro.
5. In modalità guidatore Tachimetro si attiva solo con il Bluetooth dell'auto collegato: autoradio e vivavoce li riconosce da solo, per gli altri dispositivi chiede una volta e poi ricorda. Per provarlo fuori dall'auto c'è il pulsante «Prova senza auto».
6. Se qualcosa non funziona, apri Impostazioni, Stato e manda uno screenshot.

Gli aggiornamenti si installano sopra la versione precedente senza perdere le impostazioni.

## Cosa si può dire

| Per | Di' |
|---|---|
| Navigare | «portami a casa», «portami in via Roma 10 a Napoli», «portami a casa con Waze», «torna a Google Maps», «ferma la navigazione» |
| Musica | «metti Vasco su Spotify», «metti la playlist viaggio», «pausa», «riprendi», «avanti», «alza il volume» |
| Radio | «metti Radio Deejay», «metti radio RTL», «radio successiva» |
| Telefono | «chiama Luca», «leggi i messaggi», «rispondi», «entra nella riunione»; alle chiamate in arrivo rispondi sì o no |
| App | «apri WhatsApp», «cerca Stranger Things su Netflix», «aggiungi Calendario alla barra» |
| Auto | «dov'è la macchina?», «trova benzina qui vicino», «portami al primo», «che tempo fa» |
| Modalità | «modalità passeggero», «modalità guidatore», «silenzia avvisi», «chiudi Tachimetro» |
| Altro | qualsiasi domanda va a Gemini |

## Cosa fa questa versione

- Cruscotto con mappa di Google e pannello regolabile, tema giorno e notte automatico, barra delle app personalizzabile.
- Parola chiave riconosciuta sul telefono, senza internet. I comandi vengono capiti dal servizio vocale di Google del telefono; se manca, dal modello vocale interno.
- Musica di Spotify, YouTube Music e Amazon Music; radio nazionali e regionali in streaming.
- Messaggi di WhatsApp, SMS e Teams: annuncio di chi scrive (anche il gruppo), domanda se leggere, se rispondere, rilettura e conferma prima dell'invio. Gruppi configurabili e contatti preferiti.
- Chiamate in arrivo annunciate a voce, con risposta o rifiuto dicendo sì o no.
- Non disturbare automatico mentre guidi; chiusura completa senza processi in background; chiusura un minuto dopo lo scollegamento dall'auto, con parcheggio salvato.
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
