# Apex driver port to AAPS 4.0.0 — status

NOT compiled and NOT tested: the environment this was written in has no Android SDK/Gradle.
Expect some compile errors on the first build; send them back and they get fixed.

## Done
- Core definitions: PumpType.APEX_TRUCARE_III, ManufacturerType.Apex, DoseStepSize.Apex, PumpCapability.ApexCapabilities,
  InterfaceIDs.PumpType.APEX_TRUCARE_III (+ converters), NotificationId.APEX_SERVICE_MSG / PUMP_IS_LOCKED.
- Module `:pump:apex` (build.gradle.kts, manifest, settings.gradle; app picks it up automatically).
- Protocol/command classes unchanged. Ported: ApexPump (+StateFlows), ApexDriverStatus, ApexBluetooth, ApexService
  (MetroService, NotificationManager, BolusProgressData, suspend PumpSync via runBlocking, preference flows),
  ApexPumpPlugin (new Pump interface), preference keys, Compose status screen, member injector.
- Static checks passed: every app.aaps import resolves in 4.0.0, every R.string used exists.

## Known simplifications / things to verify
- EventProfileSwitchChanged no longer exists -> EventProfileChangeRequested is sent instead (ApexService.updateBasalPatternIndex).
- Pump max bolus is no longer part of PumpDescription (only the AAPS safety limit applies; the pump rejects too large doses itself).
- Cancel-bolus button no longer writes a user-entry-log record.
- Plugin icon is a placeholder (IcPluginVirtualPump); @MetroIntKey(1100).
- FirmwareVer list hides engineering-only versions unless config.isEngineeringMode().
- Battery "custom voltage" fields are shown whenever "calculate battery %" is on (no finer visibility rule in 4.0.0 keys).
- Database from the old fork may not be compatible with 4.0.0: export settings first.

## Audit fixes also applied here (same as old-build-fixes.patch)
- deliverTreatment keeps the bolus reference (race could report a delivered bolus as failed)
- reconnect loop throttled to 1 attempt / 3 s; descriptor-write status checked; scan accepts only the configured serial
  (and no NPE on unnamed devices); BLE write chunk = MTU-3; stale "pump unreachable" notification dismissed on connect;
  updateSettings no longer crashes without a status.
