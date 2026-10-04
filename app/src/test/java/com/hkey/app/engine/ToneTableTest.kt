package com.hkey.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0 (E6/A8) — bảng dấu đầy đủ: >= 200 cặp Telex -> kết quả cho CẢ hai
 * kiểu đặt dấu (mới "hoà" / cũ "hòa"). Format mỗi mục:
 * `phím=kếtQuảKiểuMới` hoặc `phím=mới/cũ` khi hai kiểu khác nhau.
 * Khác biệt duy nhất: cụm mở oa/oe/uy — mới đặt âm 2, cũ đặt âm 1.
 */
class ToneTableTest {

    private val newEngine = TelexEngine(EngineOptions(newToneStyle = true))
    private val oldEngine = TelexEngine(EngineOptions(newToneStyle = false))

    private data class ToneCase(val keys: String, val newOut: String, val oldOut: String)

    private val cases: List<ToneCase> by lazy {
        SPEC.trim().split(Regex("\\s+")).filter { '=' in it }.map { tok ->
            val p = tok.split('=', limit = 2)
            val outs = p[1].split('/')
            ToneCase(p[0], outs[0], outs.getOrElse(1) { outs[0] })
        }
    }

    @Test
    fun toneTableCoversBothStyles() {
        assertTrue("cần >= 200 ca, hiện ${cases.size}", cases.size >= 200)
        val bad = mutableListOf<String>()
        for (c in cases) {
            val n = newEngine.transform(c.keys)
            val o = oldEngine.transform(c.keys)
            if (n != c.newOut) bad.add("${c.keys} new: $n != ${c.newOut}")
            if (o != c.oldOut) bad.add("${c.keys} old: $o != ${c.oldOut}")
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}

private val SPEC = """
# --- cụm mở oa: mới âm 2 / cũ âm 1 ---
hoaf=hoà/hòa hoas=hoá/hóa hoar=hoả/hỏa hoax=hoã/hõa hoaj=hoạ/họa
khoaf=khoà/khòa khoas=khoá/khóa khoar=khoả/khỏa khoax=khoã/khõa khoaj=khoạ/khọa
toaf=toà/tòa toas=toá/tóa toar=toả/tỏa toax=toã/tõa toaj=toạ/tọa
doaf=doà/dòa doas=doá/dóa doar=doả/dỏa ngoaf=ngoà/ngòa ngoaj=ngoạ/ngọa
soaf=soà/sòa xoaf=xoà/xòa loaf=loà/lòa loaj=loạ/lọa moaf=moà/mòa
noaf=noà/nòa roaf=roà/ròa voaf=voà/vòa phoaf=phoà/phòa thoaf=thoà/thòa
nhoaf=nhoà/nhòa troaf=troà/tròa choaf=choà/chòa ghoaf=ghoà/ghòa boaf=boà/bòa
# --- cụm mở oe: mới âm 2 / cũ âm 1 ---
khoef=khoè/khòe khoes=khoé/khóe khoer=khoẻ/khỏe khoex=khoẽ/khõe khoej=khoẹ/khọe
toef=toè/tòe toes=toé/tóe toer=toẻ/tỏe loef=loè/lòe loej=loẹ/lọe
hoef=hoè/hòe hoer=hoẻ/hỏe xoef=xoè/xòe noef=noè/nòe moef=moè/mòe
doef=doè/dòe boef=boè/bòe phoef=phoè/phòe thoef=thoè/thòe ngoef=ngoè/ngòe
soef=soè/sòe choef=choè/chòe nhoef=nhoè/nhòe troef=troè/tròe ghoef=ghoè/ghòe
# --- cụm mở uy (không sau q): mới âm 2 (y) / cũ âm 1 (u) ---
thuyf=thuỳ/thùy thuys=thuý/thúy thuyr=thuỷ/thủy thuyx=thuỹ/thũy thuyj=thuỵ/thụy
khuyf=khuỳ/khùy khuys=khuý/khúy khuyr=khuỷ/khủy khuyj=khuỵ/khụy
tuyf=tuỳ/tùy tuys=tuý/túy tuyr=tuỷ/tủy tuyx=tuỹ/tũy tuyj=tuỵ/tụy
suyf=suỳ/sùy suys=suý/súy suyr=suỷ/sủy suyj=suỵ/sụy
duyf=duỳ/dùy duys=duý/dúy duyr=duỷ/dủy duyj=duỵ/dụy
huyf=huỳ/hùy huyr=huỷ/hủy luyf=luỳ/lùy luyr=luỷ/lủy nuyf=nuỳ/nùy
xuyj=xuỵ/xụy buyf=buỳ/bùy muyr=muỷ/mủy ruyr=ruỷ/rủy vuyf=vuỳ/vùy
# --- oa/oe/uy có coda: hai kiểu giống nhau (âm 2) ---
hoanf=hoàn hoans=hoán hoanr=hoản hoanx=hoãn hoanj=hoạn
loatf=loatf loats=loát loatr=loatr loatx=loatx loatj=loạt
khoangf=khoàng khoangs=khoáng ngoanhs=ngoánh thoacs=thoác
thuytf=thuytf thuyts=thuýt tuytj=tuỵt suytf=suytf
# --- gi-: i là phụ âm ---
gif=gì gis=gí gir=gỉ gix=gĩ gij=gị
giaf=già gias=giá giar=giả giax=giã giaj=giạ
giaif=giài giais=giái giair=giải giaix=giãi giaij=giại
gief=giè gies=gié gier=giẻ giex=giẽ giej=giẹ
giof=giò gios=gió gior=giỏ
gioif=giòi giois=giói gioir=giỏi gioij=giọi
giuf=giù gius=giú giur=giủ giuj=giụ
giuwf=giừ giuws=giứ giuwr=giử giuwj=giự
giongf=giòng giongs=gióng giongr=giỏng giongj=giọng
giuowngf=giường giuowngs=giướng giuowngr=giưởng giuowngj=giượng
giayf=giày giays=giáy giayr=giảy
# --- qu-: u là phụ âm ---
quaf=quà quas=quá quar=quả quax=quã quaj=quạ
quif=quì quis=quí quij=quị
quyf=quỳ quys=quý quyr=quỷ quyj=quỵ
quef=què ques=qué quer=quẻ quej=quẹ
queof=quèo queos=quéo queor=quẻo queoj=quẹo
queef=quề quees=quế queenf=quền queens=quến queenr=quển
quyeef=quyề quyees=quyế quyeenf=quyền quyeens=quyến quyeenr=quyển
quowf=quờ quows=quớ quowr=quở quowx=quỡ quowj=quợ
quawf=quằ quaws=quắ quawr=quẳ quawj=quặ
quaif=quài quais=quái quair=quải quaij=quại
quoof=quồ quoos=quố quoor=quổ quooj=quộ
quoocf=quôcf quoocs=quốc quootf=quôtf quoots=quốt
quangf=quàng quangs=quáng quangr=quảng quangj=quạng
quenf=quèn quens=quén quenr=quẻn
# --- âm đơn ---
af=à as=á ar=ả ax=ã aj=ạ
ef=è es=é er=ẻ ex=ẽ ej=ẹ
if=ì is=í ir=ỉ ix=ĩ ij=ị
of=ò os=ó or=ỏ ox=õ oj=ọ
uf=ù us=ú ur=ủ ux=ũ uj=ụ
yf=ỳ ys=ý yr=ỷ yx=ỹ yj=ỵ
# --- nguyên âm mang dấu phụ: dấu trên nó (cả 2 kiểu) ---
aws=ắ awf=ằ awr=ẳ awx=ẵ awj=ặ
aas=ấ aaf=ầ aar=ẩ aax=ẫ aaj=ậ
ees=ế eef=ề eer=ể eex=ễ eej=ệ
oos=ố oof=ồ oor=ổ oox=ỗ ooj=ộ
ows=ớ owf=ờ owr=ở owx=ỡ owj=ợ
uws=ứ uwf=ừ uwr=ử uwx=ữ uwj=ự
awnf=ằn awns=ắn aanhf=ầnh eemf=ềm oonf=ồn ownhf=ờnh uwns=ứn
# --- ươ / uơ (E4) ---
duowc=dươc muown=mươn luowp=lươp thuowng=thương khuown=khươn
nguowi=ngươi duowt=dươt buowc=bươc nuowng=nương suowt=sươt
dduowc=đươc dduowcs=đước dduowcj=được muowif=mười nguowif=người
luowif=lười cuowif=cười duowif=dười tuowif=tười suowif=sười
uow=uơ duow=duơ muow=muơ luow=luơ huow=huơ khuow=khuơ
thuow=thuơ suow=suơ xuow=xuơ nuow=nuơ truow=truơ dduow=đuơ
thuowr=thuở huowr=huở thuows=thuớ thuowf=thuờ thuowj=thuợ
# --- 3+ nguyên âm: dấu giữa ---
xoaif=xoài xoais=xoái xoair=xoải xoaix=xoãi xoaij=xoại
ngoaif=ngoài ngoais=ngoái ngoair=ngoải ngoaix=ngoãi ngoaij=ngoại
khoaif=khoài doaif=doài boaij=boại loaif=loài toais=toái
khoauf=khoauf ngoauf=ngoauf
khoeof=khoèo khoeos=khoéo xoeof=xoèo noeof=noèo
muoif=muòi duoif=duòi tuoij=tuọi nuoij=nuọi suoif=suòi buoij=buọi
ruoij=ruọi khuoif=khuòi nguoif=nguòi
# --- uô / iê / yê (có coda hay đặt dấu trên âm phụ) ---
muoons=muốn muoonf=muồn luoonj=luộn duoonx=duỗn
buoons=buốn cuoonf=cuồn suoonj=suộn nuoons=nuốn tuoonf=tuồn
kieenf=kiền tieens=tiến tieenf=tiền dieenx=diễn kieef=kiề
yeeuf=yều yeeus=yếu yeenf=yền yeens=yến khuyeenf=khuyền khuyeens=khuyến
# --- y đứng trước nguyên âm là bán âm: dấu ở âm cuối ---
khuyaf=khuyà thuyaf=thuyà luyaf=luyà tuyaf=tuyà
khuyar=khuyả huyaj=huyạ xuyaf=xuyà
khuyef=khuỳe quyaf=quyà quyef=quyè
# --- tên riêng / hoa ---
Hoaf=Hoà/Hòa HOAF=HOÀ/HÒA DDAWNGJ=ĐẶNG Nguyeenx=Nguyễn Vieetj=Việt
VIEETJ=VIỆT Thuys=Thuý/Thúy Quys=Quý Khuyeenr=Khuyển
Anf=Àn Binhf=Bình Cux=Cũ Dungx=Dũng Haf=Hà
# --- dấu giữa từ / đè dấu / z / đúp phím / retro w ---
dasng=dáng
hoanfs=hoán hoasf=hoà/hòa hoasz=hoas bass=bas
osw=ớ asw=ắ ws=ứ honw=hơn
batx=batx bacx=bacx tets=tét tef=tè
"""
