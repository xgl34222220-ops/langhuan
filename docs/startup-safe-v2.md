# Startup Safe V2

This hotfix removes real Miuix composables from the production startup path while retaining the experimental implementations for later device validation.

Production routes:
- ReaderShelfV7 -> ReaderShelfV6
- ReaderFirstBookV7 -> ReaderFirstBookV6
- LanghuanTheme -> DynamicMaterialTheme only

The purpose is to isolate the on-device startup crash from reader feature changes.
