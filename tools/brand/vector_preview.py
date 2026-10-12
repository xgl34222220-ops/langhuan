"""Render the shipped Android vector drawables (subset used by the brand assets) to PNG via SVG.
usage: python3 vector_preview.py <res_dir> <out_dir>   (needs cairosvg + Pillow)"""
import sys, os, io, re, xml.etree.ElementTree as ET
import cairosvg
from PIL import Image, ImageDraw
A='{http://schemas.android.com/apk/res/android}'
def colors(res, night=False):
    c={}
    for d in (['values','values-night'] if night else ['values']):
        f=os.path.join(res,d,'colors.xml')
        if os.path.exists(f):
            for e in ET.parse(f).getroot(): c[e.get('name')]=e.text.strip()
    return c
def col(v,c): return c[v.split('/')[-1]] if v.startswith('@color/') else ('#'+v[3:] if len(v)==9 else v)
def to_svg(path, res, night=False):
    c=colors(res,night); root=ET.parse(path).getroot()
    vw,vh=root.get(A+'viewportWidth'),root.get(A+'viewportHeight'); defs=[]; n=[0]
    def walk(e):
        out=''
        for ch in e:
            if ch.tag=='group':
                px,py=float(ch.get(A+'pivotX','0')),float(ch.get(A+'pivotY','0'))
                sx,sy=float(ch.get(A+'scaleX','1')),float(ch.get(A+'scaleY','1'))
                tx,ty=float(ch.get(A+'translateX','0')),float(ch.get(A+'translateY','0'))
                out+=f'<g transform="translate({px+tx} {py+ty}) scale({sx} {sy}) translate({-px} {-py})">'+walk(ch)+'</g>'
            elif ch.tag=='path':
                fill=ch.get(A+'fillColor')
                if fill is None:
                    g=ch.find('.//gradient'); n[0]+=1; gid=f'g{n[0]}'
                    stops=''.join(f'<stop offset="{i.get(A+"offset")}" stop-color="{col(i.get(A+"color"),c)}"/>' for i in g.findall('item'))
                    defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A+"startX")}" y1="{g.get(A+"startY")}" x2="{g.get(A+"endX")}" y2="{g.get(A+"endY")}">{stops}</linearGradient>')
                    fill=f'url(#{gid})'
                else: fill=col(fill,c)
                out+=f'<path d="{ch.get(A+"pathData")}" fill="{fill}"/>'
        return out
    body=walk(root)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}"><defs>{"".join(defs)}</defs>{body}</svg>'
def render(path,res,size,night=False):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=to_svg(path,res,night).encode(),output_width=size,output_height=size))).convert('RGBA')
def mask(im,shape):
    n=im.size[0]; k=4; m=Image.new('L',(n*k,n*k),0); dr=ImageDraw.Draw(m); i=n*k*18/108; box=[i,i,n*k-i,n*k-i]; w=box[2]-box[0]
    if shape=='circle': dr.ellipse(box,fill=255)
    elif shape=='squircle': dr.rounded_rectangle(box,radius=w*0.32,fill=255)
    elif shape=='rounded': dr.rounded_rectangle(box,radius=w*0.16,fill=255)
    elif shape=='teardrop':
        dr.ellipse(box,fill=255); dr.rectangle([box[0]+w/2,box[1]+w/2,box[2],box[3]],fill=255)
    else: dr.rectangle(box,fill=255)
    m=m.resize((n,n),Image.LANCZOS); o=Image.new('RGBA',(n,n),(0,0,0,0)); o.paste(im,(0,0),m); c=round(n*18/108); return o.crop((c,c,n-c,n-c))
