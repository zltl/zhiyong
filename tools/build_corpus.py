"""Build app/src/main/assets/corpus.json from the practice text."""

from __future__ import annotations

import json
from pathlib import Path

# 通行临习繁体。异写待原帖核对，不并字。
LINES = """
天地玄黃宇宙洪荒
日月盈昃辰宿列張
寒來暑往秋收冬藏
閏餘成歲律呂調陽
雲騰致雨露結為霜
金生麗水玉出崑岡
劍號巨闕珠稱夜光
果珍李柰菜重芥薑
海鹹河淡鱗潛羽翔
龍師火帝鳥官人皇
始制文字乃服衣裳
推位讓國有虞陶唐
弔民伐罪周發殷湯
坐朝問道垂拱平章
愛育黎首臣伏戎羌
遐邇一體率賓歸王
鳴鳳在竹白駒食場
化被草木賴及萬方
蓋此身髮四大五常
恭惟鞠養豈敢毀傷
女慕貞絜男效才良
知過必改得能莫忘
罔談彼短靡恃己長
信使可覆器欲難量
墨悲絲染詩讚羔羊
景行維賢克念作聖
德建名立形端表正
空谷傳聲虛堂習聽
禍因惡積福緣善慶
尺璧非寶寸陰是競
資父事君曰嚴與敬
孝當竭力忠則盡命
臨深履薄夙興溫凊
似蘭斯馨如松之盛
川流不息淵澄取映
容止若思言辭安定
篤初誠美慎終宜令
榮業所基籍甚無竟
學優登仕攝職從政
存以甘棠去而益詠
樂殊貴賤禮別尊卑
上和下睦夫唱婦隨
外受傅訓入奉母儀
諸姑伯叔猶子比兒
孔懷兄弟同氣連枝
交友投分切磨箴規
仁慈隱惻造次弗離
節義廉退顛沛匪虧
性靜情逸心動神疲
守真志滿逐物意移
堅持雅操好爵自縻
都邑華夏東西二京
背邙面洛浮渭據涇
宮殿盤鬱樓觀飛驚
圖寫禽獸畫彩仙靈
丙舍旁啟甲帳對楹
肆筵設席鼓瑟吹笙
升階納陛弁轉疑星
右通廣內左達承明
既集墳典亦聚群英
杜稿鍾隸漆書壁經
府羅將相路俠槐卿
戶封八縣家給千兵
高冠陪輦驅轂振纓
世祿侈富車駕肥輕
策功茂實勒碑刻銘
磻溪伊尹佐時阿衡
奄宅曲阜微旦孰營
桓公匡合濟弱扶傾
綺回漢惠說感武丁
俊乂密勿多士寔寧
晉楚更霸趙魏困橫
假途滅虢踐土會盟
何遵約法韓弊煩刑
起翦頗牧用軍最精
宣威沙漠馳譽丹青
九州禹跡百郡秦并
嶽宗泰岱禪主云亭
雁門紫塞雞田赤城
昆池碣石鉅野洞庭
曠遠綿邈巖岫杳冥
治本於農務茲稼穡
俶載南畝我藝黍稷
稅熟貢新勸賞黜陟
孟軻敦素史魚秉直
庶幾中庸勞謙謹敕
聆音察理鑑貌辨色
貽厥嘉猷勉其祗植
省躬譏誡寵增抗極
殆辱近恥林皋幸即
兩疏見機解組誰逼
索居閑處沉默寂寥
求古尋論散慮逍遙
欣奏累遣慼謝歡招
渠荷的歷園莽抽條
枇杷晚翠梧桐蚤凋
陳根委翳落葉飄搖
遊鵾獨運凌摩絳霄
耽讀玩市寓目囊箱
易輶攸畏屬耳垣牆
具膳餐飯適口充腸
飽飫烹宰飢厭糟糠
親戚故舊老少異糧
妾御績紡侍巾帷房
紈扇圓潔銀燭煒煌
晝眠夕寐藍筍象床
弦歌酒宴接杯舉觴
矯手頓足悅豫且康
嫡後嗣續祭祀烝嘗
稽顙再拜悚懼恐惶
箋牒簡要顧答審詳
骸垢想浴執熱願涼
驢騾犢特駭躍超驤
誅斬賊盜捕獲叛亡
布射僚丸嵇琴阮嘯
恬筆倫紙鈞巧任釣
釋紛利俗並皆佳妙
毛施淑姿工顰妍笑
年矢每催曦暉朗曜
璇璣懸斡晦魄環照
指薪修祜永綏吉劭
矩步引領俯仰廊廟
束帶矜莊徘徊瞻眺
孤陋寡聞愚蒙等誚
謂語助者焉哉乎也
""".strip().splitlines()


def clamp(value: float) -> float:
    return max(0.08, min(0.92, value))


def strokes_for(char: str) -> list[dict]:
    """Connected schematic curves. Not a transcription of Zhiyong's cursive."""
    seed = sum(ord(char) * (i + 3) for i in range(1))
    count = 3 + (seed % 3)
    x = clamp(0.66 + ((seed >> 3) % 9) / 100)
    y = clamp(0.16 + ((seed >> 1) % 7) / 100)
    strokes = []
    for i in range(count):
        bend = ((seed >> (i + 2)) % 17 - 8) / 100
        dx = -0.16 - ((seed >> (i + 4)) % 9) / 100
        dy = 0.15 + ((seed >> (i + 1)) % 8) / 100
        if i % 2 == 1:
            dx = -dx * 0.55
        x2 = clamp(x + dx)
        y2 = clamp(y + dy)
        c1x = clamp(x + bend)
        c1y = clamp(y + dy * 0.35)
        c2x = clamp(x2 - bend * 0.8)
        c2y = clamp(y2 - dy * 0.25)
        kind = "silk" if i == count - 2 else "solid"
        path = (
            f"M {x:.3f} {y:.3f} "
            f"C {c1x:.3f} {c1y:.3f} {c2x:.3f} {c2y:.3f} {x2:.3f} {y2:.3f}"
        )
        strokes.append({"order": i + 1, "kind": kind, "path": path})
        x, y = x2, y2
    return strokes


ASSET_DIR = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "glyphs" / "ogawa"
FALLBACK_DIR = ASSET_DIR.parent / "guanzhong"
FALLBACK_JSON = Path(__file__).resolve().parent / "ink_fallback.json"


def fallback_kinds() -> dict[int, list[str]]:
    """index -> glyph kinds the ink edition takes from 关中本 (see pick_fallback.py)."""
    if not FALLBACK_JSON.exists():
        return {}
    out: dict[int, list[str]] = {}
    for pick in json.loads(FALLBACK_JSON.read_text(encoding="utf-8")):
        index, kind = pick["index"], pick["kind"]
        if not (FALLBACK_DIR / f"{index:03d}_{kind}.webp").exists():
            raise SystemExit(f"missing fallback glyph {index:03d}_{kind}; run pick_fallback.py")
        out.setdefault(index, []).append(kind)
    return {index: sorted(kinds, reverse=True) for index, kinds in out.items()}


def ink_stem(index: int) -> str | None:
    stem = f"glyphs/ogawa/{index:03d}"
    cao = ASSET_DIR / f"{index:03d}_cao.webp"
    zhen = ASSET_DIR / f"{index:03d}_zhen.webp"
    if cao.exists() and zhen.exists():
        return stem
    return None


def main() -> None:
    if len(LINES) != 125:
        raise SystemExit(f"expected 125 lines, got {len(LINES)}")
    bad = [line for line in LINES if len(line) != 8]
    if bad:
        raise SystemExit(f"lines not length 8: {bad}")
    text = "".join(LINES)
    if len(text) != 1000:
        raise SystemExit(f"expected 1000 chars, got {len(text)}")
    dupes = sorted({ch for ch in text if text.count(ch) > 1})
    if dupes:
        raise SystemExit("duplicate characters: " + " ".join(dupes))

    fallback = fallback_kinds()
    characters = []
    inked = 0
    for index, char in enumerate(text):
        stem = ink_stem(index)
        available = stem is not None
        if stem:
            inked += 1
        entry = {
            "index": index,
            "char": char,
            "group": index // 4,
            "slot": index % 4,
            "available": available,
            "ink": stem,
            "rubbing": None,
            "caoStrokes": strokes_for(char) if available else [],
        }
        if index in fallback:
            entry["inkFallback"] = f"glyphs/guanzhong/{index:03d}"
            entry["inkFallbackKinds"] = fallback[index]
        characters.append(entry)

    payload = {
        "schema": 1,
        "placeholderGlyphs": False,
        "note": "草书底帖为小川本墨迹切图；笔顺仍是示意图。",
        "fallbackNote": "小川本残缺处由关中本拓片补，反相后按小川本纸色调色。",
        "characters": characters,
    }
    out = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "corpus.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    patched = sum(len(k) for k in fallback.values())
    print(f"wrote {out} chars={len(characters)} available={inked} ink={inked} fallback={patched}")


if __name__ == "__main__":
    main()
