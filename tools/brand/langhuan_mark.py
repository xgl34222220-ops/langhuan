import math
IVORY="#F7F0E1"; GOLD="#E4B466"; JADE_TOP="#24785F"; JADE_BOT="#10423A"; JADE_LINE="#1B5E4C"
def arc_ring(cx,cy,r,w,a0,a1):
    # filled annular arc from angle a0 to a1 (degrees, 0=right, CCW in math, y up)-> svg coords
    def p(rad,a): return cx+rad*math.cos(math.radians(a)), cy-rad*math.sin(math.radians(a))
    ro,ri=r+w/2,r-w/2
    x0,y0=p(ro,a0); x1,y1=p(ro,a1); x2,y2=p(ri,a1); x3,y3=p(ri,a0)
    large=1 if (a1-a0)%360>180 else 0
    return (f"M{x0:.2f},{y0:.2f} A{ro},{ro} 0 {large},0 {x1:.2f},{y1:.2f} "
            f"A{w/2},{w/2} 0 0,0 {x2:.2f},{y2:.2f} A{ri},{ri} 0 {large},1 {x3:.2f},{y3:.2f} A{w/2},{w/2} 0 0,0 {x0:.2f},{y0:.2f} Z")
def paths(variant="B"):
    cx=54
    g=0.9
    top=59.0; bot=75.0; ox=33.0; oyt=55.0; oyb=69.6
    L=(f"M{cx-g},{top} Q{cx-9},{oyt-4.5} {ox},{oyt} L{ox},{oyb} Q{cx-9},{oyb-3.2} {cx-g},{bot} Z")
    R=(f"M{cx+g},{top} Q{cx+9},{oyt-4.5} {108-ox},{oyt} L{108-ox},{oyb} Q{cx+9},{oyb-3.2} {cx+g},{bot} Z")
    ring=arc_ring(cx,53.0,21.0,2.7,3,177)
    mr=4.1; mx=cx+7.0; my=41.5
    moon=f"M{mx-mr},{my} A{mr},{mr} 0 1,0 {mx+mr},{my} A{mr},{mr} 0 1,0 {mx-mr},{my} Z"
    return L,R,ring,moon,[]
