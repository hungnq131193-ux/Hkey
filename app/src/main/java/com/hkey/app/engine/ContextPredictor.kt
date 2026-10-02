package com.hkey.app.engine

/**
 * Next-word prediction (trigram -> bigram -> unigram backoff), prefix
 * completion (so khớp cả dạng không dấu), and conservative correction.
 * Lớp corpus (bigramModel/trigramModel/bosTop nạp từ res/raw) tách với lớp
 * cá nhân (userBigram + Entry.personal) để xoá/lưu dữ liệu học riêng.
 */
class ContextPredictor {

    /** Hệ số chấm điểm — gom một chỗ để chỉnh bằng bộ đánh giá offline (3.7). */
    data class Weights(
        var triPresent: Long = 1_000_000_000L, // có trigram thắng mọi backoff
        var tri: Long = 50_000L,
        var bi: Long = 200L,
        var user: Long = 2_000_000L,           // bigram cá nhân (đã học)
        var uni: Long = 1L,
        var personalBoost: Long = 200_000L,    // freq cá nhân trong completion/correction
        var adjacency: Long = 300_000_000L,    // thay 1 ký tự bằng phím kề QWERTY
        var sameBase: Long = 50_000_000L,      // ứng viên cùng thân từ (lệch dấu)
        var minMargin: Double = 1.2            // điểm thắng phải vượt á quân ×minMargin
    )

    var weights = Weights()

    private val deaccentRows = listOf(
        "aáàảãạăắằẳẵặâấầẩẫậ",
        "eéèẻẽẹêếềểễệ",
        "iíìỉĩị",
        "oóòỏõọôốồổỗộơớờởỡợ",
        "uúùủũụưứừửữự",
        "yýỳỷỹỵ"
    )

    private val vocabulary = mutableMapOf<String, Entry>().apply {
        // Từ rất phổ biến
        for (w in listOf(
            "là", "của", "và", "có", "không", "được", "tôi", "bạn", "anh", "em",
            "chị", "đi", "làm", "ở", "với", "cho", "này", "gì", "đâu", "rồi",
            "thì", "mà", "còn", "đã", "đang", "sẽ", "vẫn", "cũng", "rất", "nhé",
            "ạ", "vâng", "người", "cảm", "ơn", "nước", "hôm", "nay", "học",
            "ăn", "về", "nói", "muốn", "xem", "đến", "một", "hai", "bao", "nhiêu"
        )) put(w, Entry(w, deaccent(w), 500))
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
        )) put(w, Entry(w, deaccent(w), 350))
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
        )) put(w, Entry(w, deaccent(w), 220))
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
        )) put(w, Entry(w, deaccent(w), 200))
        values.forEach { it.fromDict = true }
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
        "được" to mutableMapOf("không" to 80, "rồi" to 70, "chưa" to 60, "gì" to 40, "luôn" to 40),
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
        "người" to mutableMapOf("ta" to 60, "khác" to 50, "đẹp" to 40, "yêu" to 40, "nước" to 30)
    )

    /** Lớp corpus lưu bằng khóa số + mảng phẳng (đỡ heap ~60MB so với map
     *  lồng nhau — ngân sách ≤15MB, 3.2). Mỗi từ có Entry.mid (id < 2^20):
     *  - biKey = midPrev shl 20 | midNext   (sắp tăng dần)
     *  - triKey = midP2 shl 40 | midP1 shl 20 | midNext
     * Tra cứu bằng nhị phân trên đoạn tiền tố. */
    private var biKeys = LongArray(0)
    private var biCnt = IntArray(0)
    private var triKeys = LongArray(0)
    private var triCnt = IntArray(0)
    private var idWord = emptyArray<String>()

    /** Bigram cá nhân (tự học) — tách khỏi corpus để xoá/lưu riêng (3.6). */
    private val userBigram = mutableMapOf<String, MutableMap<String, Int>>()

    /** Từ hay mở đầu câu (đếm từ token đầu câu của corpus). */
    private var bosTop: List<String> = emptyList()

    private fun midOf(w: String) = vocabulary[w]?.mid ?: -1

    /** Vị trí đầu tiên keys[i] >= key (hoặc size nếu không có). */
    private fun lowerBound(keys: LongArray, key: Long): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Số đếm bigram corpus (p1 -> w), 0 nếu không có. */
    private fun corpusBigram(idP: Int, idW: Int): Int {
        if (idP < 0 || idW < 0) return 0
        val i = lowerBound(biKeys, (idP.toLong() shl 20) or idW.toLong())
        return if (i < biKeys.size &&
            biKeys[i] == ((idP.toLong() shl 20) or idW.toLong())) biCnt[i] else 0
    }

    /** Số đếm trigram corpus (p2, p1 -> w), 0 nếu không có. */
    private fun corpusTrigram(idP2: Int, idP1: Int, idW: Int): Int {
        if (idP2 < 0 || idP1 < 0 || idW < 0) return 0
        val key = (idP2.toLong() shl 40) or (idP1.toLong() shl 20) or idW.toLong()
        val i = lowerBound(triKeys, key)
        return if (i < triKeys.size && triKeys[i] == key) triCnt[i] else 0
    }
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

    /** Một mục từ điển: freq = tần suất corpus/từ điển, personal = số lần
     *  người dùng gõ, lastSeen phục vụ eviction; các chỉ mục giữ tham chiếu
     *  chung nên đổi số liệu không cần dựng lại. */
    internal class Entry(
        val word: String,
        val base: String,
        var freq: Int,
        var personal: Int = 0,
        var lastSeen: Long = 0,
        var fromDict: Boolean = false,
        var mid: Int = -1 // id trong lớp corpus (-1 = không có n-gram corpus)
    )

    /**
     * Index cập nhật tăng dần: sorted/byBase/byLen là danh sách mutable giữ
     * tham chiếu Entry; từ mới chèn đúng vị trí (nhị phân), freq đổi chỉ cần
     * đánh dấu topCache bẩn. Chỉ dựng lại khi nạp từ điển hàng loạt.
     */
    internal class Index(
        val sorted: ArrayList<Entry>,                    // sắp theo base
        val byBase: HashMap<String, MutableList<Entry>>,
        val byLen: HashMap<Int, MutableList<Entry>>
    ) {
        var topCache: List<String>? = null

        fun insert(e: Entry) {
            var lo = 0
            var hi = sorted.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid].base < e.base) lo = mid + 1 else hi = mid
            }
            sorted.add(lo, e)
            byBase.getOrPut(e.base) { mutableListOf() }.add(e)
            byLen.getOrPut(e.word.length) { mutableListOf() }.add(e)
            topCache = null
        }

        fun remove(e: Entry) {
            sorted.remove(e) // hiếm gọi (eviction/xoá học) nên O(n) chấp nhận
            byBase[e.base]?.remove(e)
            byLen[e.word.length]?.remove(e)
            topCache = null
        }

        fun top(): List<String> = topCache
            ?: sorted.asSequence().filter { it.freq >= 2 || it.personal >= 2 }
                .sortedByDescending { it.freq + it.personal }.take(3)
                .map { it.word }.toList()
                .also { topCache = it }
    }

    private var indexCache: Index? = null
    private var vocabVersion = 0 // tăng khi vocabulary thêm từ mới (kiểm chồng snapshot)

    /** Từ đủ điều kiện gợi ý: từ điển/corpus (freq≥2) hoặc từ học ≥2 lần. */
    private fun eligible(e: Entry) = e.freq >= 2 || e.personal >= 2

    /** Điểm ngữ cảnh backoff: trigram > bigram > unigram + lớp cá nhân.
     *  bigramModel (viết tay) là lớp seed nhỏ giữ hành vi khi chưa nạp corpus. */
    private fun contextScore(p2: String, p1: String, w: String): Long {
        val W = weights
        val id1 = midOf(p1)
        val idw = midOf(w)
        val t = if (p2.isEmpty()) 0 else corpusTrigram(midOf(p2), id1, idw)
        val b = corpusBigram(id1, idw) + (bigramModel[p1]?.get(w) ?: 0)
        val u = userBigram[p1]?.get(w) ?: 0
        val uni = vocabulary[w]?.freq ?: 0
        return (if (t > 0) W.triPresent else 0) + t * W.tri + b * W.bi +
            u * W.user + uni * W.uni
    }
    private val index: Index
        get() = indexCache ?: buildIndex().also { indexCache = it }

    private fun buildIndex(): Index = buildIndexFrom(vocabulary.values.toList())

    /** Snapshot vocab để build chỉ mục ở thread nền (gọi trên main thread). */
    internal fun snapshotForIndex(): Pair<List<Entry>, Int> =
        vocabulary.values.toList() to vocabVersion

    /** Thuần tính toán trên snapshot — chạy được ở thread nền. Entry được
     *  share tham chiếu nên freq đổi song song vẫn thấy. */
    internal fun buildIndexFrom(entries: List<Entry>): Index {
        val sorted = ArrayList(entries.sortedBy { it.base })
        val byBase = HashMap<String, MutableList<Entry>>()
        val byLen = HashMap<Int, MutableList<Entry>>()
        for (e in entries) {
            byBase.getOrPut(e.base) { mutableListOf() }.add(e)
            byLen.getOrPut(e.word.length) { mutableListOf() }.add(e)
        }
        return Index(sorted, byBase, byLen)
    }

    /** Lắp chỉ mục dựng nền (main thread). Bỏ qua nếu vocab đã thêm từ mới
     *  sau snapshot — query kế sẽ tự dựng lại chỉ mục đủ từ. */
    internal fun installIndex(idx: Index, version: Int) {
        if (version == vocabVersion) indexCache = idx
    }

    /** Nạp thêm từ vào từ điển (file res/raw hoặc từ tự học). Từ đã có giữ
     *  nguyên tần suất cao hơn. Gọi trên main thread. */
    fun addWords(words: Collection<String>, freq: Int = 100) {
        var changed = false
        for (w in words) {
            val k = w.trim().lowercase()
            if (k.isNotEmpty() && !vocabulary.containsKey(k)) {
                vocabulary[k] = Entry(k, deaccent(k), freq, fromDict = true)
                changed = true
            }
        }
        if (changed) {
            vocabVersion++
            indexCache = null
        }
    }

    /** Nạp mô hình corpus từ vi_model.tsv (luồng nền parse xong, gọi trên
     *  main thread). Tần suất thật thay cho điểm phẳng 100 (G1). */
    fun loadModel(
        unigrams: Map<String, Int>,
        bigrams: Map<String, Map<String, Int>>,
        trigrams: Map<String, Map<String, Int>>,
        bos: List<String>
    ) {
        for ((w, f) in unigrams) {
            val e = vocabulary[w]
            if (e == null) {
                vocabulary[w] = Entry(w, deaccent(w), f, fromDict = true)
            } else {
                e.freq = f // corpus là tần suất thật
                e.fromDict = true
            }
        }
        // Gán id corpus cho mọi từ (id < 2^20 — vocab thực tế ~50k, dư rất xa).
        // Từ thêm sau (học) giữ mid=-1: không có n-gram corpus, đúng thực tế.
        if (vocabulary.size < (1 shl 20)) {
            var id = 0
            for (e in vocabulary.values) e.mid = id++
            idWord = Array(vocabulary.size) { "" }
            for (e in vocabulary.values) idWord[e.mid] = e.word
            buildFlatNgrams(bigrams, trigrams)
        }
        bosTop = bos.filter { vocabulary.containsKey(it) }
        vocabVersion++
        indexCache = null
    }

    private fun buildFlatNgrams(
        bigrams: Map<String, Map<String, Int>>,
        trigrams: Map<String, Map<String, Int>>
    ) {
        // Mảng phẳng sắp theo khóa — tra bằng nhị phân.
        val bi = ArrayList<Pair<Long, Int>>(bigrams.values.sumOf { it.size })
        for ((p, tr) in bigrams) {
            val ip = midOf(p)
            for ((n, c) in tr) {
                val iN = midOf(n)
                if (ip >= 0 && iN >= 0) bi.add((ip.toLong() shl 20 or iN.toLong()) to c)
            }
        }
        bi.sortBy { it.first }
        biKeys = LongArray(bi.size) { bi[it].first }
        biCnt = IntArray(bi.size) { bi[it].second }
        val tri = ArrayList<Pair<Long, Int>>(trigrams.values.sumOf { it.size })
        for ((k, tr) in trigrams) {
            val i2 = midOf(k.substringBefore('|'))
            val i1 = midOf(k.substringAfter('|'))
            for ((n, c) in tr) {
                val iN = midOf(n)
                if (i2 >= 0 && i1 >= 0 && iN >= 0) tri.add(
                    (i2.toLong() shl 40) or (i1.toLong() shl 20) or iN.toLong() to c
                )
            }
        }
        tri.sortBy { it.first }
        triKeys = LongArray(tri.size) { tri[it].first }
        triCnt = IntArray(tri.size) { tri[it].second }
    }

    /** Dữ liệu học để lưu/xoá (3.6): từ có personal>0 hoặc từ học mới,
     *  kèm userBigram. Đối xứng với importLearned. */
    fun exportLearned(): Pair<List<Triple<String, Int, Long>>, List<Triple<String, String, Int>>> {
        val words = vocabulary.values.filter { it.personal > 0 || !it.fromDict }
            .map { Triple(it.word, it.personal, it.lastSeen) }
        val bis = userBigram.flatMap { (p, m) -> m.map { (n, c) -> Triple(p, n, c) } }
        return words to bis
    }

    fun importLearned(words: List<Triple<String, Int, Long>>, bis: List<Triple<String, String, Int>>) {
        var changed = false
        for ((w, p, t) in words) {
            val e = vocabulary[w]
            if (e != null) {
                e.personal = p; e.lastSeen = t
            } else {
                vocabulary[w] = Entry(w, deaccent(w), 0, p, t)
                indexCache?.insert(vocabulary.getValue(w))
                changed = true
            }
        }
        for ((p, n, c) in bis) {
            userBigram.getOrPut(p) { mutableMapOf() }[n] = c
        }
        if (changed) vocabVersion++
        indexCache?.topCache = null
    }

    /** Nút "Xóa dữ liệu học": bỏ từ học, reset số liệu cá nhân trên từ điển. */
    fun clearLearned() {
        val learned = vocabulary.values.filter { !it.fromDict }
        vocabulary.values.removeAll(learned.toSet())
        indexCache?.let { idx -> learned.forEach { idx.remove(it) } }
        for (e in vocabulary.values) {
            e.personal = 0
            e.lastSeen = 0
        }
        userBigram.clear()
        indexCache?.topCache = null
    }

    /** Giới hạn số từ học; vượt thì bỏ mục ít dùng/cũ nhất (3.6). */
    fun boundLearned(max: Int) {
        val learned = vocabulary.values.filter { !it.fromDict }
            .sortedWith(compareBy({ it.personal }, { it.lastSeen }))
        val idx = indexCache
        for (e in learned.take((learned.size - max).coerceAtLeast(0))) {
            vocabulary.remove(e.word)
            idx?.remove(e)
        }
        if (learned.size > max) vocabVersion++
    }

    /** Vị trí đầu tiên có sorted[i].base >= key. */
    private fun lowerBound(sorted: List<Entry>, key: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].base < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Gợi ý từ tiếp theo: trigram(w-2, w-1) -> bigram(w-1) -> unigram, cộng
     *  lớp cá nhân. Đầu câu (previousWord rỗng) = từ hay mở câu (G4). */
    fun predictNext(previousWord: String, wordBeforePrev: String = ""): List<String> {
        val p1 = previousWord.lowercase().trim()
        val p2 = wordBeforePrev.lowercase().trim()
        if (p1.isEmpty()) return bosTop.take(3).ifEmpty { index.top() }
        val cands = HashSet<String>()
        val id1 = midOf(p1)
        val id2 = midOf(p2)
        if (id2 >= 0 && id1 >= 0) {
            // Mọi (p2,p1,next) có sẵn: quét đoạn tiền tố trong mảng trigram.
            var i = lowerBound(triKeys,
                (id2.toLong() shl 40) or (id1.toLong() shl 20))
            while (i < triKeys.size && (triKeys[i] ushr 20) ==
                ((id2.toLong() shl 20) or id1.toLong())) {
                cands.add(idWord[(triKeys[i] and 0xFFFFFL).toInt()])
                i++
            }
        }
        if (id1 >= 0) {
            var i = lowerBound(biKeys, id1.toLong() shl 20)
            while (i < biKeys.size && (biKeys[i] ushr 20) == id1.toLong()) {
                cands.add(idWord[(biKeys[i] and 0xFFFFFL).toInt()])
                i++
            }
        }
        bigramModel[p1]?.keys?.let(cands::addAll)
        userBigram[p1]?.keys?.let(cands::addAll)
        if (cands.isEmpty()) return index.top()
        return cands.asSequence()
            .filter { vocabulary[it]?.let { e -> eligible(e) } != false }
            .map { it to contextScore(p2, p1, it) }
            .sortedByDescending { it.second }.take(3).map { it.first }.toList()
    }

    /** Gợi ý hoàn thành từ theo prefix đang gõ, so khớp cả dạng không dấu;
     *  xếp hạng = tần suất + cá nhân + điểm ngữ cảnh (3.4). */
    fun completions(
        prefix: String,
        previousWord: String? = null,
        beforePrev: String? = null
    ): List<String> {
        val p = prefix.lowercase().trim()
        if (p.isEmpty()) return emptyList()
        val p1 = previousWord?.lowercase()?.trim() ?: ""
        val p2 = beforePrev?.lowercase()?.trim() ?: ""
        val base = deaccent(p)
        val idx = index
        val best = ArrayList<Pair<String, Long>>(4)
        var i = lowerBound(idx.sorted, base)
        while (i < idx.sorted.size && idx.sorted[i].base.startsWith(base)) {
            val e = idx.sorted[i]
            i++
            if (e.word == p || !eligible(e)) continue // từ mới học cần gõ ≥2 lần
            val score = e.freq + e.personal * weights.personalBoost +
                contextScore(p2, p1, e.word)
            if (best.size == 3 && score <= best[2].second) continue
            best.add(e.word to score)
            best.sortByDescending { it.second }
            if (best.size > 3) best.removeAt(3)
        }
        return best.map { it.first }
    }

    /** prefix đã gõ còn là tiền tố của từ hợp lệ khác? (3.4 — chỉ hiện bản
     *  sửa khi từ không thể là khúc đầu của từ đúng). */
    fun isPrefixOfKnownWord(word: String): Boolean {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return false
        val base = deaccent(w)
        val idx = index
        var i = lowerBound(idx.sorted, base)
        while (i < idx.sorted.size && idx.sorted[i].base.startsWith(base)) {
            val e = idx.sorted[i++]
            if (e.word != w && e.word.length > w.length && eligible(e)) return true
        }
        return false
    }

    /**
     * Phương án sửa tốt nhất cho từ đã gõ, hoặc null nếu từ hợp lệ / không có
     * phương án đủ chắc. Nhận: lệch 1 ký tự, đảo 2 ký tự kề, hoặc đặt nhầm dấu
     * ("noí" -> "nói"). 3.5: ưu tiên thay bằng phím kề QWERTY; ứng viên thắng
     * phải thắng á quân bằng minMargin hoặc có ngữ cảnh ủng hộ — không chắc
     * thì không sửa. Không sửa từ <3 ký tự hay từ chứa ký tự không phải chữ.
     */
    fun correction(
        typedWord: String,
        previousWord: String?,
        beforePrev: String? = null
    ): String? {
        val word = typedWord.lowercase().trim()
        if (word.length < 3 || word.any { !it.isLetter() }) return null
        if (vocabulary.containsKey(word)) return null
        val p1 = previousWord?.lowercase()?.trim() ?: ""
        val p2 = beforePrev?.lowercase()?.trim() ?: ""
        val base = deaccent(word)
        val idx = index

        // Ứng viên: (word, điểm mô hình, điểm đã cộng bonus, sameBase, ctx, adj)
        class Cand(val word: String, val model: Long, val boosted: Long,
                   val sameBase: Boolean, val ctx: Boolean, val adj: Boolean)
        val cands = mutableListOf<Cand>()
        val sameBaseCount = idx.byBase[base]?.count { eligible(it) } ?: 0

        fun offer(e: Entry, sameBase: Boolean) {
            val ctx = (bigramModel[p1]?.containsKey(e.word) == true) ||
                (userBigram[p1]?.containsKey(e.word) == true) ||
                corpusBigram(midOf(p1), e.mid) > 0 ||
                (p2.isNotEmpty() && corpusTrigram(midOf(p2), midOf(p1), e.mid) > 0)
            val adj = word.length == e.word.length &&
                qwertyAdjacentEdit(word, e.word)
            val model = e.freq + e.personal * weights.personalBoost +
                contextScore(p2, p1, e.word)
            var boosted = model
            if (sameBase) boosted += weights.sameBase
            if (adj) boosted += weights.adjacency
            cands.add(Cand(e.word, model, boosted, sameBase, ctx, adj))
        }

        // Cùng thân từ (thiếu/lệch dấu): tra map, không quét.
        for (e in idx.byBase[base].orEmpty()) {
            if (eligible(e)) offer(e, sameBase = true)
        }
        // Lệch/đảo 1 ký tự: chỉ quét 3 nhóm độ dài len-1..len+1.
        for (len in word.length - 1..word.length + 1) {
            for (e in idx.byLen[len].orEmpty()) {
                if (e.base == base || !eligible(e) ||
                    !editsWithinOne(word, e.word)) continue
                offer(e, sameBase = false)
            }
        }
        if (cands.isEmpty()) return null
        cands.sortByDescending { it.boosted }
        val winner = cands[0]
        val runnerUp = cands.getOrNull(1)
        if (cands.size == 1) return winner.word
        if (sameBaseCount == 1 && winner.sameBase) return winner.word
        if (winner.ctx) return winner.word // ngữ cảnh ủng hộ (giữ luật cũ)
        // Chỉ sửa khi có bằng chứng typo (phím kề / cùng thân) VÀ điểm mô hình
        // (không tính bonus) vẫn thắng á quân bằng ngưỡng — không chắc thì
        // không sửa (3.5).
        if ((winner.adj || winner.sameBase) && runnerUp != null &&
            winner.model >= runnerUp.model * weights.minMargin) return winner.word
        return null
    }

    /** Hàng phím kề nhau trên QWERTY (kể cả hàng số sát mép). */
    private val qwertyNear = let {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val m = mutableMapOf<Char, MutableSet<Char>>()
        for (row in rows) for (i in row.indices) {
            val s = m.getOrPut(row[i]) { mutableSetOf() }
            if (i > 0) s.add(row[i - 1])
            if (i < row.length - 1) s.add(row[i + 1])
            // phím chéo hàng trên/dưới
        }
        // liên kết chéo giữa các hàng
        for (r in 0 until rows.size - 1) for (i in rows[r].indices) {
            for (k in listOf(i - 1, i, i + 1)) {
                if (k in rows[r + 1].indices) {
                    m.getOrPut(rows[r][i]) { mutableSetOf() }.add(rows[r + 1][k])
                    m.getOrPut(rows[r + 1][k]) { mutableSetOf() }.add(rows[r][i])
                }
            }
        }
        m
    }

    /** a và b cùng độ dài, khác đúng 1 ký tự và hai ký tự đó là phím kề. */
    private fun qwertyAdjacentEdit(a: String, b: String): Boolean {
        var diff = -1
        for (i in a.indices) if (a[i] != b[i]) {
            if (diff >= 0) return false
            diff = i
        }
        if (diff < 0) return false
        return qwertyNear[deaccent(a[diff].toString())[0]]
            ?.contains(deaccent(b[diff].toString())[0]) == true
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

    /** Tự học: từ mới vào vocab (freq=0, personal++) — gợi ý sau ≥2 lần gõ;
     *  chuỗi từ ghi vào lớp cá nhân userBigram, không đụng corpus (3.2/3.6). */
    fun recordSequence(prev: String, current: String, now: Long = System.currentTimeMillis()) {
        val p = prev.lowercase().trim()
        val c = current.lowercase().trim()
        // Chỉ học từ toàn chữ cái, độ dài hợp lý — không học chuỗi số/ký hiệu (2.3).
        if (c.isEmpty() || c.length > 24 || c.any { !it.isLetter() }) return
        val e = vocabulary[c]
        if (e != null) {
            e.personal++
            e.lastSeen = now
            indexCache?.topCache = null
        } else {
            vocabulary[c] = Entry(c, deaccent(c), 0, personal = 1, lastSeen = now)
                .also { indexCache?.insert(it) }
            vocabVersion++
        }
        if (p.isEmpty() || p.length > 24 || p.any { !it.isLetter() }) return
        val transitions = userBigram.getOrPut(p) { mutableMapOf() }
        transitions[c] = (transitions[c] ?: 0) + 1
    }
}
