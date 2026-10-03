# HKey Phase 1 — P0 fixes (from user's brief 2026-10-03)

## Spec
- `hu.io.vn` phải giữ nguyên thường; chỉ viết hoa khi sau dấu kết câu có khoảng trắng ("Xin chào. Tôi").
- URL/email/IP/dotted tokens: không bật hoa, không gợi ý, không tự sửa.

## Task 1 (plan 1.1): auto-cap + context boundary + dotted-token guard
- updateAutoShift: getCursorCapsMode(inputType) làm nguồn chính; fallback regex [.!?…]["')\]]*\s+$ hoặc xuống dòng.
- TextContext.lastTwo: "." dính liền không phải đầu câu.
- Guard: từ gõ dính sau .@/: -> không gợi ý, không tự sửa, không học.
- Tests: hu.io.vn, 1.5, a.b@c.com giữ nguyên; "Chào. Tôi", "Ok?! Vâng", "\"Hi.\" Tôi", xuống dòng -> hoa.

## Task 2 (plan 1.2): giữ chữ hoa
- TelexEngine.transform: mask hoa/thường theo từng ký tự (USA->USA, VN->VN, iPhone->iPhone, ĐƯỢC->ĐƯỢC).
- commitComposing + acceptSuggestion: áp lại kiểu hoa của chữ đã gõ (matchCase); gợi ý đầu câu hiện hoa.

## Tasks sau (theo thứ tự plan): 1.3 Telex EN/URL restore + luật c/ch/p/t; 1.4 tách NO_SUGGESTIONS; 1.5 Enter performEditorAction; 1.6 contextCache theo expected-cursor; 1.7 backspace xoá ký tự hiển thị; 1.8 resumeWord giữ dấu; 1.9 CI fail khi thiếu secret ký; 1.10 nhỏ (LearningStore bỏ dòng hỏng, race clear-learned, deleteRepeat khi ẩn, inset SDK35, fullscreen landscape).
Phase 2-5: engine state-machine + VNI/Simple/Quick Telex + bỏ dấu cũ/mới + spellcheck/macro/EN-VI; KeyboardView tự vẽ + preview/long-press/hàng số/caps-lock/globe/emoji/clipboard/dark/double-space/swipe-space/TalkBack; từ điển sạch + model mmap + học giữ hoa; test Robolectric + R8 + CI signing + ma trận app.
