package com.hkey.app.engine

/**
 * Next-word prediction (bigram), prefix completion (so khớp cả dạng không
 * dấu để gợi ý ngay khi đang gõ), and conservative correction. Correction
 * chỉ sửa khi từ lệch đúng 1 ký tự so với từ có trong từ điển.
 */
class ContextPredictor {

    private val vocabulary = mutableMapOf<String, Int>().apply {
        // Từ rất phổ biến
        for (w in listOf(
            "là", "của", "và", "có", "không", "được", "tôi", "bạn", "anh", "em",
            "chị", "đi", "làm", "ở", "với", "cho", "này", "gì", "đâu", "rồi",
            "thì", "mà", "còn", "đã", "đang", "sẽ", "vẫn", "cũng", "rất", "nhé",
            "ạ", "vâng", "người", "cảm", "ơn", "nước", "hôm", "nay", "học",
            "ăn", "về", "nói", "muốn", "xem", "đến", "một", "hai", "bao", "nhiêu"
        )) put(w, 500)
        // Phổ biến
        for (w in listOf(
            "mình", "họ", "ta", "nó", "ấy", "cậu", "chú", "bác", "cô", "ông", "bà",
            "con", "ngày", "tháng", "năm", "giờ", "phút", "sáng", "trưa", "chiều",
            "tối", "đêm", "khuya", "lúc", "khi", "bây", "mai", "qua", "mốt",
            "tuần", "sớm", "muộn", "ba", "bốn", "năm", "sáu", "bảy", "tám",
            "chín", "mười", "trăm", "nghìn", "triệu", "tỷ", "vào", "ra", "lên",
            "xuống", "sang", "qua", "trong", "ngoài", "trên", "dưới", "trước",
            "sau", "giữa", "cạnh", "bên", "theo", "từ", "tại", "vì", "nên",
            "nhưng", "nếu", "dù", "tuy", "hoặc", "hay", "bị", "phải", "cần",
            "thể", "đây", "đó", "kia", "đấy", "chứ", "nhỉ", "ừ", "dạ", "ôi",
            "chưa", "mới", "xong", "hết", "cả", "mỗi", "từng", "riêng", "chung",
            "cùng", "khác", "giống", "như", "kiểu", "lại", "nữa", "ngay", "vừa",
            "hơn", "nhất", "quá", "lắm", "hơi", "thôi", "đi", "thật", "luôn",
            "xin", "lỗi", "chào", "chúc", "mừng", "nào", "gian", "đầu", "bụng",
            "lưng", "chân", "tay", "mặt", "mỏi", "miệng", "vẻ", "đời", "nãy",
            "chút", "thứ", "lâu", "nhiên", "gấp", "hộp", "khỏi", "dốc", "đỉnh",
            "tiên", "gói", "góp", "giặt", "nhau", "ơi", "sao", "rằng",
            "gặp", "gọi", "gửi", "nhận", "trả", "lời", "hỏi", "nghĩ", "biết",
            "hiểu", "nhớ", "quên", "tin", "yêu", "thích", "ghét", "thương",
            "nhờ", "giúp", "mua", "bán", "đổi", "lấy", "nghe", "nhìn", "thấy",
            "đọc", "viết", "vẽ", "hát", "nhảy", "chạy", "đứng", "ngồi", "nằm",
            "ngủ", "thức", "uống", "mở", "đóng", "tắt", "bật", "chờ", "đợi",
            "chơi", "cười", "khóc", "tắm", "rửa", "nấu", "mặc", "đeo", "cầm",
            "mang", "đưa", "sửa", "thay", "thêm", "bớt", "tăng", "giảm", "tính",
            "đếm", "chọn", "tìm", "kiếm", "tải", "đăng", "nhập", "lưu", "xóa",
            "xoá", "huỷ", "ghi", "chép", "chụp", "quay", "dừng", "tiếp", "tục",
            "thử", "thắng", "thua", "sống", "chết", "sinh", "kết", "bắt"
        )) put(w, 350)
        // Danh từ thông dụng
        for (w in listOf(
            "cơm", "phở", "bún", "cháo", "bánh", "mì", "trà", "sữa", "bia",
            "rượu", "thuốc", "gạo", "thịt", "cá", "tôm", "cua", "gà", "vịt",
            "heo", "bò", "rau", "củ", "quả", "trái", "cây", "hoa", "lá",
            "tiền", "bạc", "vàng", "đồng", "nhà", "cửa", "phòng", "bếp", "bàn",
            "ghế", "giường", "tủ", "đèn", "quạt", "xe", "máy", "đạp", "đường",
            "phố", "ngõ", "cầu", "sông", "biển", "núi", "đồi", "ruộng", "vườn",
            "trường", "lớp", "sách", "vở", "bút", "mực", "giấy", "thước",
            "cặp", "điện", "thoại", "màn", "hình", "chuột", "phím", "loa",
            "cáp", "sạc", "pin", "việc", "công", "ty", "quan", "văn", "hàng",
            "chợ", "siêu", "thị", "bệnh", "viện", "ngân", "trạm", "bến", "sân",
            "bay", "ga", "tàu", "thuyền", "khách", "sạn", "nghỉ", "quán",
            "gia", "đình", "bố", "mẹ", "cha", "trai", "gái", "vợ", "chồng",
            "bè", "sếp", "nhân", "viên", "sinh", "giáo", "sĩ", "tá", "sư",
            "xế", "an", "bộ", "đội", "giám", "đốc", "phó", "trưởng", "chính",
            "phủ", "tổng", "lương", "thưởng", "phạt", "thuế", "phí", "giá",
            "cước", "đơn", "hợp", "giấy", "tờ", "chứng", "minh", "chiếu",
            "bằng", "chỉ", "lái", "số", "địa", "tên", "tuổi", "tộc", "mật",
            "khẩu", "tài", "khoản", "mã", "otp", "link", "wifi", "mạng",
            "internet", "dữ", "liệu", "file", "tập", "tin", "ảnh", "video",
            "phim", "nhạc", "bài", "hát", "khúc", "game", "trò", "nhắn",
            "cuộc", "họp", "câu", "chuyện", "vấn", "đề", "kết", "nguyên",
            "mục", "đích", "ý", "kiến", "quyết", "định", "kế", "hoạch", "dự",
            "án", "báo", "cáo", "thông", "hệ", "thống", "phần", "mềm", "cứng",
            "ứng", "dụng", "web", "trang", "sản", "phẩm", "hoá", "trường",
            "kinh", "tế", "doanh", "buôn", "dịch", "vụ", "lịch", "tour", "vé",
            "điểm", "bản", "đồ", "thời", "tiết", "nắng", "mưa", "gió", "bão",
            "lũ", "sương", "mây", "trời", "đất", "lửa", "khí", "rác", "rừng",
            "chim", "chó", "mèo", "trâu", "dê", "ngỗng", "mực", "ốc", "rắn",
            "hổ", "tử", "voi", "khỉ", "gấu", "sói", "thỏ", "sức", "khoẻ",
            "cúm", "sốt", "ho", "đau", "nhức", "mệt", "say", "sức", "thầy",
            "việt", "nam", "nội", "gòn", "hồ", "nẵng", "huế", "hải", "thơ",
            "trang", "lạt", "vũng", "nhơn", "quốc", "long", "miền", "bắc",
            "tỉnh", "thành", "quận", "huyện", "xã", "phường", "thôn", "làng",
            "khu", "tổ", "quê", "tivi"
        )) put(w, 220)
        // Tính từ / phó từ
        for (w in listOf(
            "đẹp", "xấu", "tốt", "dở", "ngon", "ngọt", "đắng", "chua", "cay",
            "mặn", "nhạt", "béo", "gầy", "mập", "ốm", "cao", "thấp", "lùn",
            "to", "nhỏ", "bé", "lớn", "dài", "ngắn", "rộng", "hẹp", "sâu",
            "nông", "xa", "gần", "nhanh", "chậm", "nóng", "lạnh", "ấm", "mát",
            "khô", "ướt", "sạch", "bẩn", "dơ", "cũ", "trẻ", "già", "đúng",
            "sai", "giả", "khó", "dễ", "vui", "buồn", "sướng", "khổ", "hạnh",
            "phúc", "mắn", "xui", "đen", "khoẻ", "đói", "no", "khát", "bận",
            "rảnh", "rỗi", "rộn", "giàu", "nghèo", "hèn", "đông", "vắng",
            "đúc", "ồn", "yên", "tĩnh", "lặng", "êm", "dịu", "nhẹ", "nặng",
            "cứng", "rắn", "lỏng", "mỏng", "dày", "sắc", "bén", "cùn", "nhọn",
            "vuông", "tròn", "méo", "thẳng", "cong", "chéo", "đỏ", "cam",
            "lục", "lam", "chàm", "tím", "trắng", "xám", "nâu", "hồng", "kim",
            "nhiều", "ít", "vài", "mấy", "đầy", "vơi", "thiếu", "thừa", "đủ",
            "tuyệt", "dễ", "ghê", "giỏi", "kém", "ngu", "khôn", "ngoan",
            "hư", "lười", "chăm", "siêng", "khoẻ"
        )) put(w, 200)
    }

    private val bigramModel = mutableMapOf<String, MutableMap<String, Int>>(
        "hôm" to mutableMapOf("nay" to 100, "qua" to 60, "kia" to 20),
        "ngày" to mutableMapOf("mai" to 90, "kia" to 30, "hôm" to 40),
        "đi" to mutableMapOf("làm" to 120, "học" to 80, "chơi" to 70, "đâu" to 60, "về" to 60, "ăn" to 50, "ngủ" to 50, "đây" to 30),
        "cà" to mutableMapOf("phê" to 150),
        "cảm" to mutableMapOf("ơn" to 140),
        "xin" to mutableMapOf("chào" to 130, "lỗi" to 110, "phép" to 60),
        "chuyển" to mutableMapOf("khoản" to 130, "tiền" to 90),
        "làm" to mutableMapOf("việc" to 110, "gì" to 80, "ăn" to 50, "sao" to 40, "được" to 40),
        "ăn" to mutableMapOf("cơm" to 110, "gì" to 60, "sáng" to 50, "trưa" to 50, "tối" to 50, "phở" to 40, "uống" to 30),
        "không" to mutableMapOf("có" to 90, "được" to 80, "biết" to 70, "sao" to 60, "đâu" to 50, "phải" to 40, "thể" to 40, "cần" to 30),
        "có" to mutableMapOf("không" to 100, "gì" to 60, "thể" to 70, "việc" to 40, "thời" to 30),
        "của" to mutableMapOf("tôi" to 90, "anh" to 60, "em" to 60, "mình" to 50, "bạn" to 50),
        "uống" to mutableMapOf("nước" to 120, "trà" to 60, "bia" to 50, "thuốc" to 40, "sữa" to 40),
        "xem" to mutableMapOf("phim" to 100, "gì" to 50, "tivi" to 40, "tin" to 40),
        "nghe" to mutableMapOf("nhạc" to 90, "máy" to 40, "tin" to 30),
        "ngủ" to mutableMapOf("ngon" to 100, "đi" to 50, "chưa" to 30),
        "chúc" to mutableMapOf("mừng" to 120, "ngủ" to 80, "sức" to 60, "bạn" to 50),
        "rất" to mutableMapOf("vui" to 90, "đẹp" to 70, "ngon" to 70, "tốt" to 60, "nhiều" to 50, "hay" to 50),
        "tôi" to mutableMapOf("đi" to 80, "có" to 70, "muốn" to 70, "yêu" to 60, "thích" to 60, "đang" to 60, "cần" to 60, "là" to 50, "không" to 60, "sẽ" to 50),
        "bạn" to mutableMapOf("có" to 60, "đang" to 60, "ơi" to 70, "muốn" to 40, "thích" to 40, "không" to 40, "bè" to 40),
        "bây" to mutableMapOf("giờ" to 150),
        "bao" to mutableMapOf("nhiêu" to 130, "giờ" to 70, "lâu" to 40, "nhiên" to 20),
        "thế" to mutableMapOf("nào" to 100, "này" to 60, "không" to 40, "giới" to 30),
        "như" to mutableMapOf("vậy" to 80, "thế" to 70, "nào" to 60, "cũ" to 40),
        "máy" to mutableMapOf("tính" to 100, "bay" to 60, "giặt" to 40, "lạnh" to 40, "ảnh" to 30),
        "điện" to mutableMapOf("thoại" to 130, "nước" to 20),
        "công" to mutableMapOf("việc" to 110, "ty" to 90, "an" to 40, "nhân" to 30),
        "gia" to mutableMapOf("đình" to 120),
        "học" to mutableMapOf("sinh" to 80, "tập" to 40, "gì" to 40, "không" to 30, "ở" to 30),
        "sinh" to mutableMapOf("viên" to 80, "nhật" to 70, "ra" to 30),
        "giáo" to mutableMapOf("viên" to 80),
        "bác" to mutableMapOf("sĩ" to 80),
        "đồng" to mutableMapOf("ý" to 90, "nghiệp" to 50, "tiền" to 30, "hồ" to 20),
        "việt" to mutableMapOf("nam" to 150),
        "hà" to mutableMapOf("nội" to 140),
        "sài" to mutableMapOf("gòn" to 140),
        "hồ" to mutableMapOf("chí" to 120, "bơi" to 30, "nước" to 30),
        "chí" to mutableMapOf("minh" to 130),
        "tài" to mutableMapOf("khoản" to 100, "liệu" to 50, "xế" to 30),
        "mật" to mutableMapOf("khẩu" to 120),
        "thông" to mutableMapOf("tin" to 100, "báo" to 40),
        "tin" to mutableMapOf("nhắn" to 110, "tức" to 40, "tôi" to 30),
        "cuộc" to mutableMapOf("họp" to 80, "gọi" to 70, "sống" to 60, "chơi" to 30, "hẹn" to 40),
        "sức" to mutableMapOf("khoẻ" to 120),
        "thời" to mutableMapOf("tiết" to 80, "gian" to 60, "điểm" to 40),
        "hoá" to mutableMapOf("đơn" to 80),
        "nhắn" to mutableMapOf("tin" to 90),
        "gửi" to mutableMapOf("cho" to 80, "tiền" to 60, "tin" to 40, "anh" to 40, "em" to 40),
        "cho" to mutableMapOf("tôi" to 80, "em" to 70, "anh" to 60, "mình" to 60, "bạn" to 50, "hỏi" to 30),
        "mua" to mutableMapOf("gì" to 60, "sắm" to 50, "hàng" to 40, "đồ" to 40, "vé" to 30, "nhà" to 30),
        "bán" to mutableMapOf("hàng" to 60, "nhà" to 40, "xe" to 40, "đất" to 30),
        "đọc" to mutableMapOf("sách" to 70, "tin" to 50, "báo" to 40),
        "nấu" to mutableMapOf("cơm" to 90, "ăn" to 60, "gì" to 40),
        "mở" to mutableMapOf("cửa" to 80, "máy" to 60, "điện" to 40, "hộp" to 30),
        "đóng" to mutableMapOf("cửa" to 80, "gói" to 40, "tiền" to 40, "góp" to 30),
        "gặp" to mutableMapOf("nhau" to 70, "anh" to 50, "em" to 50, "bạn" to 50, "mặt" to 40, "chuyện" to 30),
        "nói" to mutableMapOf("chuyện" to 80, "gì" to 60, "với" to 50, "lại" to 40, "thật" to 40, "đi" to 40),
        "nhìn" to mutableMapOf("thấy" to 80, "gì" to 40, "đẹp" to 30),
        "thấy" to mutableMapOf("không" to 60, "gì" to 50, "chưa" to 50, "sao" to 40, "vui" to 40, "buồn" to 30),
        "quên" to mutableMapOf("mất" to 70, "rồi" to 50, "đi" to 30),
        "nhớ" to mutableMapOf("em" to 60, "anh" to 60, "quá" to 50, "về" to 40, "gì" to 30),
        "đợi" to mutableMapOf("tôi" to 60, "em" to 50, "anh" to 50, "chút" to 40, "lâu" to 30),
        "tốt" to mutableMapOf("lắm" to 80, "quá" to 70, "nhất" to 60, "hơn" to 50, "nghiệp" to 40),
        "đẹp" to mutableMapOf("quá" to 90, "lắm" to 70, "trai" to 60, "gái" to 40, "nhất" to 40),
        "ngon" to mutableMapOf("quá" to 80, "lắm" to 60, "miệng" to 50, "nhất" to 30),
        "vui" to mutableMapOf("quá" to 80, "lắm" to 70, "vẻ" to 60, "nhất" to 30),
        "buồn" to mutableMapOf("quá" to 70, "lắm" to 60, "ngủ" to 50),
        "mệt" to mutableMapOf("quá" to 80, "lắm" to 70, "mỏi" to 60, "chưa" to 20),
        "đói" to mutableMapOf("quá" to 70, "rồi" to 60, "lắm" to 60),
        "khát" to mutableMapOf("nước" to 90, "quá" to 40),
        "bận" to mutableMapOf("quá" to 70, "lắm" to 60, "rộn" to 60, "gì" to 40, "không" to 40),
        "rảnh" to mutableMapOf("không" to 70, "rỗi" to 50, "quá" to 40),
        "khoẻ" to mutableMapOf("không" to 80, "lại" to 50, "mạnh" to 60, "chưa" to 30),
        "đau" to mutableMapOf("đầu" to 70, "bụng" to 70, "quá" to 50, "lưng" to 40, "chân" to 30, "tay" to 30),
        "ở" to mutableMapOf("đâu" to 100, "nhà" to 80, "đây" to 60, "đó" to 40, "đấy" to 40, "trong" to 40, "ngoài" to 40, "trên" to 30, "lại" to 30),
        "về" to mutableMapOf("nhà" to 100, "đây" to 60, "quê" to 60, "đi" to 40, "rồi" to 50, "việt" to 30),
        "vào" to mutableMapOf("nhà" to 70, "đây" to 60, "trong" to 50, "bếp" to 40, "học" to 30, "được" to 30),
        "ra" to mutableMapOf("ngoài" to 60, "đây" to 60, "sân" to 50, "phố" to 40, "khỏi" to 40, "sao" to 30, "đi" to 40, "vào" to 30),
        "lên" to mutableMapOf("đây" to 70, "kế" to 50, "tiếp" to 40, "xe" to 40, "đỉnh" to 30, "giường" to 30, "lớp" to 30, "hình" to 30),
        "xuống" to mutableMapOf("đây" to 60, "dưới" to 50, "xe" to 50, "dốc" to 30),
        "trong" to mutableMapOf("nhà" to 50, "khi" to 50, "đó" to 50, "lúc" to 40, "này" to 40),
        "ngoài" to mutableMapOf("trời" to 60, "kia" to 50, "đường" to 40, "ra" to 30),
        "trên" to mutableMapOf("đường" to 60, "mạng" to 60, "đây" to 50, "này" to 40, "đời" to 40, "xe" to 30),
        "dưới" to mutableMapOf("nhà" to 60, "kia" to 50, "này" to 40, "đây" to 40, "trời" to 30),
        "trước" to mutableMapOf("khi" to 80, "mặt" to 60, "đây" to 50, "giờ" to 40, "tiên" to 40),
        "sau" to mutableMapOf("khi" to 80, "đó" to 70, "này" to 60, "bữa" to 40),
        "khi" to mutableMapOf("nào" to 90, "đó" to 60, "về" to 40, "nãy" to 30, "mới" to 30),
        "nếu" to mutableMapOf("không" to 70, "có" to 60, "muốn" to 50, "được" to 40, "thế" to 40),
        "nhưng" to mutableMapOf("mà" to 60, "không" to 60, "vẫn" to 50, "tôi" to 40, "cũng" to 40),
        "vì" to mutableMapOf("sao" to 80, "vậy" to 60, "thế" to 50, "anh" to 40, "em" to 40),
        "nên" to mutableMapOf("đi" to 60, "làm" to 50, "mua" to 50, "thử" to 40, "xem" to 40, "nhớ" to 30),
        "muốn" to mutableMapOf("đi" to 70, "ăn" to 60, "mua" to 60, "xem" to 50, "nói" to 50, "hỏi" to 50, "gì" to 40, "biết" to 40, "gặp" to 40, "ngủ" to 30),
        "cần" to mutableMapOf("gì" to 60, "tiền" to 50, "giúp" to 50, "mua" to 40, "gấp" to 40, "làm" to 40, "phải" to 30),
        "phải" to mutableMapOf("đi" to 60, "làm" to 60, "không" to 60, "học" to 40, "trả" to 30, "chờ" to 30),
        "được" to mutableMapOf("không" to 80, "rồi" to 70, "không" to 80, "chưa" to 60, "gì" to 40, "luôn" to 40),
        "biết" to mutableMapOf("gì" to 60, "rồi" to 60, "không" to 60, "chưa" to 50, "sao" to 40, "đâu" to 40),
        "cũng" to mutableMapOf("được" to 70, "không" to 60, "có" to 50, "thế" to 40, "vậy" to 40, "đi" to 40),
        "vẫn" to mutableMapOf("chưa" to 60, "đang" to 50, "còn" to 50, "không" to 40, "thế" to 40),
        "chưa" to mutableMapOf("có" to 60, "được" to 50, "biết" to 50, "xong" to 50, "ăn" to 40, "về" to 40, "ngủ" to 30),
        "đã" to mutableMapOf("xong" to 60, "ăn" to 50, "được" to 40, "có" to 40, "về" to 40, "đến" to 30),
        "đang" to mutableMapOf("làm" to 80, "ở" to 60, "đi" to 60, "ăn" to 50, "gì" to 50, "đâu" to 40, "học" to 40, "nấu" to 30, "chạy" to 30),
        "sẽ" to mutableMapOf("đến" to 70, "về" to 60, "làm" to 50, "đi" to 50, "có" to 50, "không" to 40, "gửi" to 40, "gặp" to 30, "báo" to 30),
        "hết" to mutableMapOf("rồi" to 80, "tiền" to 70, "pin" to 60, "hàng" to 50, "việc" to 40, "chưa" to 30),
        "xong" to mutableMapOf("rồi" to 90, "việc" to 60, "chưa" to 40),
        "mọi" to mutableMapOf("người" to 100, "thứ" to 50, "lúc" to 40, "việc" to 40),
        "người" to mutableMapOf("ta" to 60, "khác" to 50, "đẹp" to 40, "yêu" to 40, "nước" to 30),
        "thế" to mutableMapOf("nào" to 100, "này" to 60, "không" to 40, "giới" to 30)
    )

    private val deaccentRows = listOf(
        "aáàảãạăắằẳẵặâấầẩẫậ",
        "eéèẻẽẹêếềểễệ",
        "iíìỉĩị",
        "oóòỏõọôốồổỗộơớờởỡợ",
        "uúùủũụưứừửữự",
        "yýỳỷỹỵ"
    )

    /** Bỏ hết dấu thanh + dấu phụ để so khớp ("hô" -> "ho", "điện" -> "dien"). */
    private fun deaccent(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                if (c == 'đ') 'd'
                else deaccentRows.firstOrNull { c in it }?.get(0) ?: c
            )
        }
        return sb.toString()
    }

    /**
     * Index dựng một lần trên vocabulary, invalidate khi tự học/nạp từ điển.
     * Giữ O(1)-O(log n) cho mọi truy vấn khi từ điển lên tới hàng chục nghìn từ:
     * sorted+bases cho prefix search, byBase cho lỗi đặt dấu, byLen cho lỗi
     * lệch 1 ký tự, top cho fallback của predictNext.
     */
    private class Index(
        val sorted: List<Triple<String, String, Int>>, // (từ, không dấu, freq) sắp theo base
        val bases: Array<String>,                       // base song song với sorted, để binary search
        val byBase: Map<String, List<Pair<String, Int>>>,
        val byLen: Map<Int, List<Triple<String, String, Int>>>,
        val top: List<String>
    )

    private var indexCache: Index? = null
    private val index: Index
        get() = indexCache ?: buildIndex().also { indexCache = it }

    private fun buildIndex(): Index {
        val entries = vocabulary.entries.map { Triple(it.key, deaccent(it.key), it.value) }
        val sorted = entries.sortedBy { it.second }
        val byBase = HashMap<String, MutableList<Pair<String, Int>>>()
        val byLen = HashMap<Int, MutableList<Triple<String, String, Int>>>()
        for (e in entries) {
            byBase.getOrPut(e.second) { mutableListOf() }.add(e.first to e.third)
            byLen.getOrPut(e.first.length) { mutableListOf() }.add(e)
        }
        val top = entries.sortedByDescending { it.third }.map { it.first }.take(3)
        return Index(sorted, Array(sorted.size) { sorted[it].second }, byBase, byLen, top)
    }

    /** Nạp thêm từ vào từ điển (file res/raw hoặc từ tự học). Từ đã có giữ
     *  nguyên tần suất cao hơn. Gọi trên main thread. */
    fun addWords(words: Collection<String>, freq: Int = 100) {
        var changed = false
        for (w in words) {
            val k = w.trim().lowercase()
            if (k.isNotEmpty() && !vocabulary.containsKey(k)) {
                vocabulary[k] = freq
                changed = true
            }
        }
        if (changed) indexCache = null
    }

    /** Vị trí đầu tiên có bases[i] >= key. */
    private fun lowerBound(bases: Array<String>, key: String): Int {
        var lo = 0
        var hi = bases.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (bases[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Gợi ý từ tiếp theo theo từ liền trước; fallback = từ phổ biến nhất. */
    fun predictNext(previousWord: String): List<String> {
        val prev = previousWord.lowercase().trim()
        val nextWords = bigramModel[prev]
        return if (!nextWords.isNullOrEmpty()) {
            nextWords.entries.sortedByDescending { it.value }.map { it.key }.take(3)
        } else {
            index.top
        }
    }

    /** Gợi ý hoàn thành từ theo prefix đang gõ, so khớp cả dạng không dấu;
     *  từ hay đi sau từ trước được ưu tiên lên trước. */
    fun completions(prefix: String, previousWord: String? = null): List<String> {
        val p = prefix.lowercase().trim()
        if (p.isEmpty()) return emptyList()
        val base = deaccent(p)
        val boost = bigramModel[previousWord?.lowercase()?.trim()] ?: emptyMap()
        val idx = index
        val best = ArrayList<Pair<String, Int>>(4)
        var i = lowerBound(idx.bases, base)
        while (i < idx.sorted.size && idx.sorted[i].second.startsWith(base)) {
            val e = idx.sorted[i]
            i++
            if (e.first == p) continue
            val score = e.third + (boost[e.first] ?: 0) * 3
            if (best.size == 3 && score <= best[2].second) continue
            best.add(e.first to score)
            best.sortByDescending { it.second }
            if (best.size > 3) best.removeAt(3)
        }
        return best.map { it.first }
    }

    /**
     * Phương án sửa tốt nhất cho từ đã gõ, hoặc null nếu từ hợp lệ / không có
     * phương án đủ chắc. Nhận: lệch 1 ký tự, đảo 2 ký tự kề, hoặc đặt nhầm dấu
     * ("noí" -> "nói"). Nhiều phương án cùng gần thì chỉ sửa khi phương án
     * thắng có bigram với từ trước ủng hộ — không đoán bừa.
     */
    fun correction(typedWord: String, previousWord: String?): String? {
        val word = typedWord.lowercase().trim()
        if (word.isEmpty() || vocabulary.containsKey(word)) return null
        val boost = bigramModel[previousWord?.lowercase()?.trim()] ?: emptyMap()
        val base = deaccent(word)
        val idx = index

        var best: String? = null
        var bestScore = -1L
        var bestSameBase = false
        var nearCount = 0
        val sameBaseCount = idx.byBase[base]?.size ?: 0
        // Cùng thân từ (thiếu/lệch dấu): tra map, không quét.
        for ((cand, freq) in idx.byBase[base].orEmpty()) {
            nearCount++
            val score = freq.toLong() + (boost[cand] ?: 0) * 10 + 1000
            if (score > bestScore) {
                bestScore = score
                best = cand
                bestSameBase = true
            }
        }
        // Lệch/đảo 1 ký tự: chỉ quét 3 nhóm độ dài len-1..len+1.
        for (len in word.length - 1..word.length + 1) {
            for ((cand, candBase, freq) in idx.byLen[len].orEmpty()) {
                if (candBase == base || !editsWithinOne(word, cand)) continue
                nearCount++
                val score = freq.toLong() + (boost[cand] ?: 0) * 10
                if (score > bestScore) {
                    bestScore = score
                    best = cand
                    bestSameBase = false
                }
            }
        }
        if (nearCount == 1) return best
        if (sameBaseCount == 1 && bestSameBase) return best
        return if (best != null && (boost[best] ?: 0) > 0) best else null
    }

    /**
     * Lệch tối đa 1 lần sửa: thay 1 ký tự, thêm/bớt 1 ký tự, hoặc đảo 2 ký tự
     * kề nhau (Damerau). O(n), không cấp phát — thay Levenshtein đầy đủ.
     */
    private fun editsWithinOne(a: String, b: String): Boolean {
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > 1) return false
        if (n == m) {
            var diffs = 0
            var first = -1
            for (i in 0 until n) {
                if (a[i] != b[i]) {
                    if (++diffs > 2) return false
                    if (first < 0) first = i
                }
            }
            if (diffs <= 1) return true
            return a[first] == b[first + 1] && a[first + 1] == b[first] &&
                a.regionMatches(first + 2, b, first + 2, n - first - 2)
        }
        val long = if (n > m) a else b
        val short = if (n > m) b else a
        var i = 0
        var j = 0
        var skipped = false
        while (j < short.length) {
            if (i < long.length && long[i] == short[j]) {
                i++; j++
            } else {
                if (skipped) return false
                skipped = true; i++
            }
        }
        return true
    }

    /** Tự học: ghi nhận chuỗi từ người dùng gõ. */
    fun recordSequence(prev: String, current: String) {
        val p = prev.lowercase().trim()
        val c = current.lowercase().trim()
        if (c.isEmpty()) return
        vocabulary[c] = (vocabulary[c] ?: 0) + 1
        indexCache = null
        if (p.isEmpty()) return
        val transitions = bigramModel.getOrPut(p) { mutableMapOf() }
        transitions[c] = (transitions[c] ?: 0) + 1
    }
}
