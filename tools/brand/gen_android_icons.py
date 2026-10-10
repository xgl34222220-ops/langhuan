import sys,os
from langhuan_mark import *
RES=sys.argv[1]
import re
_r=lambda d: re.sub(r'-?\d+\.\d+', lambda m: ('%.2f'%float(m.group())).rstrip('0').rstrip('.'), d)
L,R,ring,moon=[_r(x) for x in paths()[:4]]
SC=0.86
def vec(body, extra_ns=""):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 琅嬛 brand mark V95: a moon gate (月洞门) arching over an open book, with a rising moon.
     Generated from tools/brand/langhuan_mark.py; edit there and regenerate. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{extra_ns}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
'''
def grp(inner, sc=SC):
    return f'''    <group android:pivotX="54" android:pivotY="54" android:scaleX="{sc}" android:scaleY="{sc}">
{inner}    </group>'''
def p(d,fill): return f'        <path android:fillColor="{fill}" android:pathData="{d}" />\n'
w=lambda name,s: open(os.path.join(RES,name),'w').write(s)
w("drawable/ic_launcher_foreground_v95.xml", vec(grp(p(ring,IVORY)+p(moon,GOLD)+p(L,IVORY)+p(R,IVORY))))
w("drawable/ic_launcher_monochrome_v95.xml", vec(grp(p(ring,"#FF000000")+p(moon,"#FF000000")+p(L,"#FF000000")+p(R,"#FF000000"))))
w("drawable/ic_launcher_background_v95.xml", vec(f'''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108">
                <item android:offset="0" android:color="{JADE_TOP}" />
                <item android:offset="1" android:color="{JADE_BOT}" />
            </gradient>
        </aapt:attr>
    </path>''', '\n    xmlns:aapt="http://schemas.android.com/aapt"'))
# splash: theme-coloured mark, slightly larger (fits the 2/3 splash circle)
w("drawable/splash_mark_v95.xml", vec(grp(p(ring,"@color/splash_mark_v95")+p(moon,"@color/splash_moon_v95")+p(L,"@color/splash_mark_v95")+p(R,"@color/splash_mark_v95"), 1.3)))
# in-app vector (24dp canvas, no padding) for empty states / headers
w("drawable/langhuan_mark_v95.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 琅嬛 mark for in-app use (empty states, about). Tint-free: colours come from the caller. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="56"
    android:viewportHeight="56">
    <group android:translateX="-26" android:translateY="-25">
{p(ring,"#FF1E6A5A")}{p(moon,"#FFC08A3A")}{p(L,"#FF1E6A5A")}{p(R,"#FF1E6A5A")}    </group>
</vector>
''')
print("ok")
