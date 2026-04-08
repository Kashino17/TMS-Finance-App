# TMS Banking — Design Spec

Personal Finance Tracking App für Samsung Galaxy Fold 7 mit Self-Hosted Backend.

## Ziel

Eine App, die automatisch alle Bankkonten synchronisiert und einen vollständigen Überblick über die eigenen Finanzen bietet. Fokus auf Transparenz — wo geht das Geld hin, wie viel ist da, wie entwickelt sich das Vermögen.

## Systemarchitektur

Zwei Komponenten, verbunden über Tailscale VPN:

### Backend (MacBook, Self-Hosted)

- Läuft auf dem MacBook des Users (nicht immer an)
- Tech: Kotlin/JVM oder Python — wird im Implementierungsplan entschieden
- Datenbank: SQLite
- Verantwortlich für: Bank-Synchronisation, Auto-Kategorisierung, Währungsumrechnung, REST API
- Erreichbar über Tailscale-Netzwerk

### Android App (Galaxy Fold 7, Native Kotlin)

- Native Android App in Kotlin
- Jetpack Compose für UI
- Lokaler SQLite-Cache für Offline-Zugriff
- NotificationListenerService für Echtzeit-Transaktionen (Mashreq, FAB)
- Optimiert für das Faltdisplay des Galaxy Fold 7

### Kommunikation

- App ↔ Backend über Tailscale VPN (verschlüsselt, kein öffentliches Internet)
- REST API auf dem Backend
- App cached alle Daten lokal und funktioniert auch offline

## Sync-Strategie

### Laufender Sync
- Solange das MacBook an ist: alle 15 Minuten neue Transaktionen abrufen
- Pro Konto wird ein `last_sync_timestamp` gespeichert

### Startup Catch-Up
- Beim Hochfahren des MacBooks: pro Konto prüfen, wann zuletzt synchronisiert wurde
- Alle Transaktionen seit dem letzten Sync nachholen
- Banking-APIs (Lean, Revolut, FinTS) speichern Transaktionshistorie serverseitig — Catch-Up ist daher zuverlässig

### Initial Full Sync
- Bei Erstverbindung einer Bank: komplette verfügbare Transaktionshistorie importieren
- Reichweite abhängig von der API (Lean: variabel, Revolut: komplett, FinTS: ca. 90 Tage)

### Notification-Bridge
- Wenn Backend offline: App fängt Notifications lokal auf
- Wenn Backend wieder online: App sendet gesammelte Notifications ans Backend
- Backend gleicht diese mit den offiziellen API-Daten beim nächsten Sync ab (Deduplizierung)

## Banking-Integrationen

### Lean Technologies — UAE Banken
- **Emirates NBD**: Kontostände ✅, Transaktionshistorie ✅, Echtzeit ❌
- **Mashreq Bank**: Kontostände ✅, Transaktionshistorie ✅, Echtzeit ✅ (Notification Listener, teilweise)
- **First Abu Dhabi Bank (FAB)**: Kontostände ✅, Transaktionshistorie ✅, Echtzeit ✅ (Notification Listener, teilweise)
- **VIO Bank**: Kontostände ✅, Transaktionshistorie ✅, Echtzeit ❌

Einmalige Verknüpfung über Lean Link SDK. Danach automatischer Zugriff über Lean API.

### Revolut API — Direkt
- Kontostände ✅, Transaktionshistorie ✅, Multi-Währung ✅
- Kein Webhook (Backend nicht öffentlich erreichbar) — Polling wie andere Banken
- OAuth-basierte Authentifizierung über das Backend

### FinTS/HBCI — Sparkasse
- Kontostände ✅, Transaktionshistorie ✅ (ca. 90 Tage)
- Direkte Verbindung zur Bank, kein Drittanbieter
- PIN + TAN bei Ersteinrichtung

### Staytris Bank — Hongkong (Manuell)
- Kein API-Zugang verfügbar
- Manuelle Eingabe über die App
- Niedrige Priorität (wenig Transaktionsvolumen)

## Datenmodell

### accounts
- `id`, `name`, `bank`, `currency`, `type`, `last_sync_at`, `balance`, `is_active`

### transactions
- `id`, `account_id`, `amount`, `currency`, `amount_aed` (umgerechnet), `date`, `merchant_name`, `description`, `category_id`, `source` (lean/revolut/fints/notification/manual), `raw_data`

### categories
- `id`, `name`, `icon`, `color`, `parent_id`
- Hierarchisch: Hauptkategorie → Unterkategorie

### exchange_rates
- `id`, `from_currency`, `to_currency`, `rate`, `date`
- Täglich aktualisiert, historisch gespeichert

### sync_log
- `id`, `account_id`, `started_at`, `finished_at`, `status`, `transactions_fetched`, `error`

## Auto-Kategorisierung

Dreistufige Strategie:

1. **Merchant-Matching** — Bekannte Händlernamen werden direkt zugeordnet (z.B. "Carrefour" → Lebensmittel, "Uber" → Transport, "DEWA" → Nebenkosten)
2. **Keyword-Matching** — Fallback über Schlüsselwörter in Beschreibung/Händlername (z.B. "Restaurant" → Essen)
3. **User-Feedback** — Manuelle Korrekturen werden gelernt: Wenn der User einen Händler umkategorisiert, gilt das für alle zukünftigen Transaktionen dieses Händlers

Nicht zuordenbare Transaktionen landen in "Sonstiges".

### Vordefinierte Kategorien
Einkommen, Lebensmittel, Restaurants, Transport, Shopping, Nebenkosten, Miete, Gesundheit, Unterhaltung, Abos, Transfers (zwischen eigenen Konten), Sonstiges

## Währungshandling

- **Leitwährung:** AED
- Jede Transaktion speichert `amount` (Originalwährung) + `amount_aed` (umgerechnet zum Tageskurs)
- Wechselkurse werden täglich über kostenlose API aktualisiert (z.B. exchangerate.host)
- Historische Kurse werden gespeichert für korrekte Rückrechnung

### Anzeige
- Standard: Originalwährung pro Transaktion
- Toggle: Alles in AED umschalten
- Gesamtvermögen auf Home Screen: immer in AED
- Konto-Detail: Originalwährung des Kontos

### Erkannte Währungen
AED, EUR, HKD + weitere Revolut-Wallets (USD, GBP etc.)

## App-Screens

### 1. Home Screen (Hauptscreen)
- **Zugeklappt:** Gesamtvermögen (große Zahl), darunter kompakte Kontoliste mit Salden
- **Aufgeklappt:** Split-View — links Konten mit Salden, rechts letzte Transaktionen über alle Konten

### 2. Konto-Detail
- Balance des Kontos, Monatsübersicht (Eingänge/Ausgaben)
- Vollständige Transaktionsliste des Kontos
- Letzter Sync-Zeitpunkt

### 3. Kategorien-Übersicht
- Donut-Chart mit Ausgabenverteilung nach Kategorie
- Aufschlüsselung mit Beträgen und Prozenten
- Filterbar nach Zeitraum (Monat, Quartal, Jahr)

### 4. Manuelle Eingabe
- Für Staytris und Bargeld-Transaktionen
- Felder: Betrag, Währung, Konto, Kategorie, Beschreibung, Datum

### 5. Einstellungen
- Bankverbindungen verwalten
- Kategorien anpassen
- Sync-Status und -Intervall
- Leitwährung ändern
- Backend-Verbindung (Tailscale IP/Port)

### Navigation
Bottom Navigation Bar: Home, Kategorien, Hinzufügen (+), Einstellungen

## Fold-Optimierung (Galaxy Fold 7)

- **Zugeklappt (Cover Display):** Kompaktes UI, einzelne Spalte, wichtigste Infos (Vermögen + Kontoliste)
- **Aufgeklappt (Großes Display):** Split-View wo sinnvoll (Konten + Transaktionen, Chart + Details)
- Erkennung über `WindowInfoTracker` API (Jetpack WindowManager)
- Nahtloser Übergang beim Auf-/Zuklappen — kein State-Verlust

## Nicht im Scope

- Budgetierung / Budget-Limits
- Gemeinsame Nutzung / Multi-User
- Export-Funktionen (PDF/CSV)
- iOS-Version
- Push-Benachrichtigungen von der eigenen App
