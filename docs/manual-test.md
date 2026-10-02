# Kịch bản thử tay trên điện thoại

Chạy sau mỗi giai đoạn, trước khi chuyển giai đoạn tiếp theo.

## Gõ tiếng Việt cơ bản
- App nhắn tin/Zalo: gõ câu dài có dấu, có `dd`, `w`, `z` (vd "đường", "hơn", "hoasz"→"hoas").

## Ô nhạy cảm (kiểm tra B1 — đúng từ Giai đoạn 2.1)
- Thanh địa chỉ trình duyệt: `www.example.com/pass` — phải ra nguyên văn.
- Ô mật khẩu: `Pass123` — không bị đổi ký tự, không có gợi ý.
- Ô email: `ten.ho@mail.com`.

## Ngữ cảnh & con trỏ
- Ô tìm kiếm / ghi chú nhiều dòng: xuống dòng, viết hoa đầu câu.
- Chạm đổi vị trí con trỏ giữa câu rồi gõ tiếp.
- Bôi chọn một từ rồi gõ đè.

## Tự sửa & hoàn tác
- Gõ sai 1 từ → bấm ⌫ ngay sau để hoàn tác.
- Chạm từ gốc trên thanh gợi ý để khôi phục nguyên văn.
- Gõ từ đúng nhưng lạ (tên riêng, tiếng Anh) → không bị sửa oan.
- Đang gõ dở (từ còn là tiền tố từ khác) → ô giữa hiện từ đang gõ, không hiện "bản sửa".

## Gợi ý & học (từ Giai đoạn 3)
- Gõ một từ lạ (tên riêng thường viết) 2 lần → lần 3 gõ khúc đầu phải thấy gợi ý.
- Ngay sau dấu cách / đầu câu: 3 ô hiện từ đoán trước hợp lý.
- Gợi ý hoàn thành phải ưu tiên từ hay đi sau từ trước (không còn xếp chữ cái).

## Vòng đời & dữ liệu học
- Đổi ô nhập liên tục; chuyển app; khoá/mở màn hình.
- Gõ một từ lạ vài lần → khởi động lại máy → gợi ý vẫn nhớ (lưu bền).
- MainActivity → "Xóa dữ liệu học" → gợi ý từ lạ mất hẳn.

## Cảm nhận tốc độ
- Gõ nhanh liên tục 30 giây; so với bản cũ.
- Bàn phím hiện nhanh, không khựng sau khi chốt từ.
- Bản debug: `adb logcat -s HKeyIME` xem thời gian `handleCharacter`/`onStartInputView`.
