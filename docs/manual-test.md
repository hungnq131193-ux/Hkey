# Kịch bản thử tay trên điện thoại

Chạy sau mỗi giai đoạn, trước khi chuyển giai đoạn tiếp theo.

## Sửa 1.1.1 — kiểm tra riêng
- **⌫ xoá:** chạm-nhả ⌫ = xoá đúng 1 ký tự; giữ = xoá liên tục, dừng ngay
  khi nhả hoặc trượt sang phím khác (không xoá vô hạn).
- **Phím nổi:** bong bóng hiện đúng ngay trên phím đang bấm, kể cả hàng trên
  cùng (tràn lên thanh gợi ý) — trên máy Android 15 edge-to-edge.
- **Rung/âm:** tắt rung trong HKey → quay lại gõ: không rung kể cả nhấn giữ
  phím có ký tự phụ. Bật lại thì rung ngay, không cần restart.
- **Hàng phím dưới:** bấm `,` ra dấu phẩy; giữ `,` mở trang emoji (icon 😊
  góc phím); giữ `VI`/`EN` đổi IME; space rộng hơn, không đè phím kề.
- **Emoji:** chọn 1 emoji → tự về trang chữ; không thấy phím "ma" khi đang
  giữ nhiều ngón mà đổi trang.
- **Dải phụ:** nhấn giữ `e` → dải `3 ê` hiện trên phím, trượt chọn, nhả ra
  ký tự đúng.

## Gõ tiếng Việt cơ bản
- App nhắn tin/Zalo: gõ câu dài có dấu, có `dd`, `w`, `z` (vd "đường", "hơn", "hoasz"→"hoas").
- **Đặt dấu giữa cụm nguyên âm (1.4.5):** `phari`→`phải`, `thari`→`thải`,
  `hori`→`hỏi`, `phasi`→`phái`. Tiếng Anh giữ nguyên: `taxi`, `visa`,
  `using`, `music`, `paris`.
- **Đúp nguyên âm đơn sau phím dấu (1.4.5):** `motoj`→`một`,
  `totos`→`tốt`, `botoj`→`bột`. Giữ nguyên: `photos`, `data`, `moto`,
  `mono`, `delete`, `banana`.

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
- **1.5.0 — từ Anh/mã không bị cắt chữ:** `plan `→`plan ` (không `lan`),
  `code `→`code ` (không `coe`), `max `→`mã ` theo Telex nhưng không bao
  giờ ra `ma` và ô cuối gợi ý luôn là `max` để chọn; trên VNI `max`,
  `plan` ra nguyên văn.
- **1.5.0 — sau hoàn tác không sửa lặp:** gõ `khoogn `→`không`, ⌫ về
  `khoogn`, space chốt lại → giữ `khoogn`; gõ `khoogn ` lần nữa vẫn giữ.
- **1.5.0 — đổi ô không sửa lùi:** gõ `khoogn ` rồi chuyển ngay sang ô
  khác/app khác trước khi bản sửa nền kịp chạy → ô cũ giữ `khoogn`,
  ô mới không bị dán chữ lạ.
- **1.5.0 — dòng mới không treo từ cũ:** gõ một câu có gợi ý, ENTER
  xuống dòng → thanh gợi ý trống/làm mới ngay, không còn chữ của dòng
  trước; chạm dời con trỏ sang chỗ khác cũng vậy.
- **1.5.0 — lối thoát phím thô:** gõ `max` → hiển thị `mã`, ô cuối
  thanh gợi ý là `max`; chạm `max` → chốt `max `; gõ `max` lần sau
  giữ nguyên luôn. Tương tự `taxi`/`visa` nếu bị bẻ.
- **1.5.0 — viết tắt phụ âm:** `ddc `→`đc `, `dd `→`đ `, `Ddc `→`Đc `;
  `window `→`window ` vẫn giữ nguyên văn.
- Đang gõ dở (từ còn là tiền tố từ khác) → ô giữa hiện từ đang gõ, không hiện "bản sửa".

## Gợi ý & học (từ Giai đoạn 3)
- Gõ một từ lạ (tên riêng thường viết) 2 lần → lần 3 gõ khúc đầu phải thấy gợi ý.
- Ngay sau dấu cách / đầu câu: 3 ô hiện từ đoán trước hợp lý.
- Gợi ý hoàn thành phải ưu tiên từ hay đi sau từ trước (không còn xếp chữ cái).
- **1.5.0 — 3 ô không trùng:** gõ `duoc` → ô giữa và 2 ô bên phải là 3
  chữ khác nhau, không ô nào lặp.

## Vòng đời & dữ liệu học
- Đổi ô nhập liên tục; chuyển app; khoá/mở màn hình.
- Gõ một từ lạ vài lần → khởi động lại máy → gợi ý vẫn nhớ (lưu bền).
- MainActivity → "Xóa dữ liệu học" → gợi ý từ lạ mất hẳn.

## Cảm nhận tốc độ
- Gõ nhanh liên tục 30 giây; so với bản cũ.
- Bàn phím hiện nhanh, không khựng sau khi chốt từ.
- Bản debug: `adb logcat -s HKeyIME` xem thời gian `handleCharacter`/`onStartInputView`.
