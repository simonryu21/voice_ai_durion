# YouTube voice search

## Flow

1. Existing STT produces the user's utterance.
2. The existing supervisor selects `open_youtube` from the app catalog. For
   `BTS 노래 틀어줘` and `Play BTS music`, the query is `BTS`. An explicitly named
   music app takes precedence over this default.
3. The handoff dispatcher renders an Android execution path with
   `action=android.intent.action.SEARCH` and `extras={"query": "BTS"}`.
   Extras are literal strings, while URI placeholders remain URL encoded.
4. The existing frontend forwards `execution_paths` unchanged to `AppHandoff.launch`.
5. Android builds an action-only Intent, adds the string extras, binds it to
   `com.google.android.youtube`, and calls `startActivity`.

YouTube is in its own video catalog category so the existing two-candidates-per-category
limit for music apps cannot exclude its tool. Android execution only is supported.
The operation opens search results; it does not automatically play a video. Launch
failures use the existing handoff error path. No frontend protocol change is needed.

## Validation

ADB on the connected SM-S942N confirmed that the SEARCH action with the `query`
extra opens BTS results. Python tests cover catalog selection without inventory,
required queries, literal Korean/special-character extras, and existing URI rendering.

After restarting the backend and installing the updated Android APK, start a fresh
voice session and try both example utterances. Confirm `open_youtube` in backend
logs, `AppHandoff.launch SUCCESS type=intent` in Android logs, and `BTS` in YouTube's
search field. Also test an explicit music-app request and an empty-query request.
Full speech-to-app validation requires the running backend/frontend and updated APK;
ADB alone does not exercise the model's tool selection.
