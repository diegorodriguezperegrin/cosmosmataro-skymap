#!/usr/bin/env python3
"""
Generate art_metadata.json from index.json anchor data.

Uses pixel-to-HIP anchor mappings to compute alignment parameters
(center RA/Dec, scale, rotation) for all constellation art.
"""

import json
import math
from pathlib import Path

# HIP star coordinates (RA in degrees, Dec in degrees)
# Source: SIMBAD/Hipparcos catalog
HIP_CATALOG = {
    # Orion
    27989: (88.7929, 7.4070),    # Betelgeuse
    22449: (72.4600, 6.9613),    # Pi3 Ori (shield)
    27366: (86.9391, -9.6696),   # Saiph
    26311: (83.8583, -1.9425),   # Mintaka
    26727: (85.1896, -1.9428),   # Alnilam
    25930: (81.2829, 6.3497),    # Bellatrix
    24436: (78.6346, -8.2016),   # Rigel
    28614: (90.5958, -6.7529),   # Eta Ori
    29426: (92.9850, 14.7686),   # Mu Ori
    
    # Andromeda
    113726: (345.4800, 42.3297), # Mirach area
    9640: (31.0958, 35.6206),    # Gamma And
    3693: (11.8217, 29.0904),
    
    # Aquila
    93244: (284.9058, 13.8633),  # Altair area
    99473: (302.8263, -0.8213),
    93805: (286.3525, 4.8817),
    
    # Aquarius
    114341: (347.3617, -15.8208),
    102618: (311.9192, -9.4958),
    107380: (326.0392, -16.6617),
    
    # Apus
    84969: (260.4875, -67.7697),
    81065: (248.4333, -78.8972),
    72370: (221.9625, -79.0447),
    
    # Ara
    85792: (263.4000, -49.8764),
    85727: (263.2417, -53.1603),
    83081: (254.6550, -49.9019),
    
    # Aries
    9884: (31.7933, 23.4625),    # Hamal
    15737: (50.6888, 17.9492),
    12832: (41.2758, 21.0292),
    
    # Auriga
    24608: (79.1725, 45.9981),   # Capella
    28380: (89.9300, 44.9478),
    23015: (74.2483, 33.1661),
    
    # Bootes
    73555: (225.4925, 40.3906),
    76267: (233.6721, 26.7147),  # Alphekka
    69673: (213.9150, 19.1822),  # Arcturus
    
    # Camelopardalis
    15863: (51.0808, 53.4858),
    41704: (127.5658, 60.7181),
    11767: (37.8758, 59.4639),
    
    # Capricornus
    100064: (304.4133, -12.5081),
    107556: (326.7600, -16.1275),
    102978: (312.9550, -17.2328),
    
    # Cassiopeia
    3179: (10.1267, 56.5372),    # Schedar
    2599: (8.3033, 54.5253),
    6686: (21.4542, 60.2353),
    
    # Centaurus
    68933: (211.6708, -36.3694),
    71683: (219.9008, -60.8353),  # Alpha Cen
    59196: (182.0892, -50.7222),
    
    # Cepheus
    116727: (354.8358, 77.6322),
    110609: (336.1292, 57.5033),
    99655: (303.0867, 64.6281),
    
    # Cetus
    3419: (10.8967, -17.9867),   # Diphda
    14146: (45.5696, -23.6244),
    12828: (41.2346, -8.1833),
    
    # Chamaeleon
    60000: (184.5867, -79.3119),
    51839: (158.8658, -77.4844),
    40702: (124.6300, -80.5400),
    
    # Canis Major
    32349: (101.2871, -16.7161),  # Sirius
    35904: (111.7875, -29.3031),
    30122: (95.0750, -17.9558),
    
    # Canis Minor
    36425: (112.8917, 5.9461),
    37279: (114.8250, 5.2250),   # Procyon
    39311: (120.5817, 8.2894),
    
    # Cancer
    43103: (131.6713, 28.7600),
    44066: (134.6213, 11.8578),
    40526: (124.1292, 9.1856),
    
    # Columba
    30122: (95.0750, -17.9558),
    25859: (83.0079, -34.0742),
    31685: (99.4279, -35.7675),
    
    # Coma Berenices
    64394: (198.0250, 27.8764),
    60742: (186.7350, 28.2683),
    64241: (197.4971, 17.5292),
    
    # Corona Australis
    92308: (282.4133, -37.9044),
    94114: (287.3679, -42.0958),
    91875: (281.1929, -36.7614),
    
    # Corona Borealis
    78493: (240.3617, 29.1053),
    76952: (235.6854, 26.2958),
    76127: (233.2321, 31.3592),
    
    # Corvus
    59316: (182.5313, -16.5158),
    59803: (184.0621, -17.5419),
    64962: (199.7296, -23.1706),
    
    # Crater
    53740: (164.9438, -17.6839),
    56633: (174.1704, -14.7786),
    58188: (179.0713, -18.3506),
    
    # Crux
    59747: (183.7863, -63.0989),
    62434: (191.9296, -59.6886),
    60718: (186.6496, -63.0992),
    
    # Canes Venatici
    67301: (206.8854, 49.3133),
    61317: (188.4354, 38.3181),
    69879: (214.5138, 35.7158),
    
    # Cygnus
    94779: (289.2763, 27.9597),
    107310: (325.0250, 45.2803),
    95947: (292.4263, 27.9597),
    
    # Delphinus
    102532: (311.3233, 11.3031),
    101421: (308.3025, 15.9119),
    102633: (311.6621, 12.1692),
    
    # Dorado
    19893: (63.9083, -61.9419),
    19780: (63.5629, -62.4731),
    34481: (107.0975, -37.0972),
    
    # Draco
    87833: (269.1517, 51.4889),  # Thuban area
    47193: (144.1879, 61.5144),
    97433: (297.0417, 70.2681),
    
    # Equuleus
    104521: (317.5854, 10.1317),
    107315: (325.0379, 3.8200),
    104987: (318.9658, 5.2478),
    
    # Eridanus
    24436: (78.6346, -8.2016),   # Rigel (shared)
    13701: (44.1067, -8.8983),
    10602: (34.1271, -51.5117),
    
    # Gemini
    36850: (113.6496, 31.8883),  # Pollux
    35350: (109.3792, 16.3994),
    31681: (99.4275, 16.3994),   # Castor
    
    # Grus
    108085: (328.4821, -37.3647),
    109268: (331.5292, -39.5428),
    114421: (347.5879, -43.5197),
    
    # Hercules
    91262: (279.2346, 38.7836),  # Vega area
    79992: (244.9354, 21.4897),
    84345: (258.6621, 24.8392),
    
    # Hydra
    43109: (131.6938, -5.9458),
    61941: (190.3767, -22.8269),
    64962: (199.7296, -23.1706),
    
    # Hydrus
    9236: (29.6908, -61.5694),
    17678: (56.8138, -74.2392),
    107089: (325.5233, -77.2544),
    
    # Indus
    101772: (309.3917, -47.2914),
    105319: (319.9658, -58.4542),
    103227: (313.7004, -47.2914),
    
    # Lacerta
    110609: (336.1292, 57.5033),
    109937: (334.2079, 44.3856),
    111104: (337.8221, 43.2683),
    
    # Leo
    47908: (146.4625, 14.5722),
    54879: (168.5271, 20.5231),  # Regulus
    53229: (163.3279, 34.2147),
    
    # Leo Minor
    53229: (163.3279, 34.2147),
    48833: (149.4713, 36.7072),
    45860: (140.2646, 34.2147),
    
    # Lepus
    24244: (78.0733, -11.8692),
    28910: (91.5354, -14.1681),
    23685: (76.3654, -22.3714),
    
    # Libra
    74785: (229.2517, -9.3828),
    72622: (222.7192, -16.0419),
    76333: (233.8817, -14.7892),
    
    # Lupus
    78918: (241.7021, -38.3972),
    73273: (224.6329, -43.1339),
    74395: (228.0717, -52.0992),
    
    # Lynx
    44248: (135.1821, 43.1883),
    30060: (94.9308, 59.0106),
    28360: (89.8821, 44.9478),
    
    # Lyra
    92862: (283.6258, 36.8986),
    94713: (287.3679, 32.6897),  # Vega
    89826: (274.9671, 33.3628),
    
    # Monoceros
    30419: (95.7413, -7.0336),
    29651: (93.7138, -6.2747),
    39863: (122.0000, -0.4928),
    
    # Musca
    63613: (195.5383, -68.1081),
    62322: (191.5704, -68.1081),
    61199: (188.0950, -72.1333),
    
    # Musca Borealis
    13061: (42.0542, 27.2625),
    12489: (40.1621, 19.7264),
    13209: (42.4958, 27.9181),
    
    # Argo Navis (Navis)
    30438: (95.9879, -52.6956),   # Canopus
    43409: (132.6338, -47.3367),
    48774: (149.2154, -54.9678),
    
    # Orion - additional
    25336: (81.1192, -2.3972),   # Epsilon Ori
    22509: (72.6533, 8.9003),
    22845: (73.5629, 2.4408),
    22730: (73.1996, 5.6053),
    23123: (74.6375, 1.7139),
    
    # Pavo
    100751: (306.4117, -56.7350),
    98495: (300.1383, -72.9103),
    86929: (266.6079, -65.0297),
    
    # Pegasus
    113963: (346.1900, 15.2050),  # Markab
    113881: (346.0467, 28.0831),  # Scheat
    107354: (325.3692, 9.8750),
    
    # Perseus
    19812: (63.8179, 48.4092),
    12777: (41.0354, 49.8614),   # Algol
    18246: (58.5333, 40.0103),   # Mirphak
    
    # Phoenix
    2081: (6.5708, -42.3058),
    6867: (22.0913, -43.3183),
    5348: (17.0963, -55.2458),
    
    # Piscis Austrinus
    113368: (344.4129, -29.6222),  # Fomalhaut
    111954: (340.1638, -32.5458),
    107608: (326.8354, -30.8981),
    
    # Additional stars for other constellations...
    # (Adding more as needed)
}

def angular_distance(ra1, dec1, ra2, dec2):
    """Compute angular distance in degrees between two points."""
    ra1, dec1, ra2, dec2 = map(math.radians, [ra1, dec1, ra2, dec2])
    
    # Haversine formula
    dra = ra2 - ra1
    ddec = dec2 - dec1
    a = math.sin(ddec/2)**2 + math.cos(dec1) * math.cos(dec2) * math.sin(dra/2)**2
    return math.degrees(2 * math.asin(math.sqrt(a)))

def pixel_distance(p1, p2):
    """Euclidean distance between two pixel positions."""
    return math.sqrt((p2[0] - p1[0])**2 + (p2[1] - p1[1])**2)

def compute_alignment(anchors, image_size):
    """
    Compute center (RA, Dec), scale (degrees), and rotation from anchor points.
    
    Returns: (ra_center, dec_center, scale_degrees, rotation_degrees)
    """
    if len(anchors) < 2:
        return None
    
    # Get sky coordinates for each anchor
    sky_coords = []
    pixel_coords = []
    for anchor in anchors:
        hip = anchor['hip']
        if hip not in HIP_CATALOG:
            print(f"  Warning: HIP {hip} not in catalog, skipping")
            continue
        ra, dec = HIP_CATALOG[hip]
        sky_coords.append((ra, dec))
        pixel_coords.append(anchor['pos'])
    
    if len(sky_coords) < 2:
        return None
    
    # Compute center as average of sky coordinates
    ra_center = sum(c[0] for c in sky_coords) / len(sky_coords)
    dec_center = sum(c[1] for c in sky_coords) / len(sky_coords)
    
    # Compute scale from anchor separations
    # Use the first two anchors
    p1, p2 = pixel_coords[0], pixel_coords[1]
    s1, s2 = sky_coords[0], sky_coords[1]
    
    px_dist = pixel_distance(p1, p2)
    sky_dist = angular_distance(s1[0], s1[1], s2[0], s2[1])
    
    if sky_dist < 0.001 or px_dist < 1:
        return None
    
    scale_px_per_deg = px_dist / sky_dist
    
    # Image width in degrees
    width, height = image_size
    scale_degrees = width / scale_px_per_deg
    
    # Compute rotation: angle from image-North to sky-North
    # Image coordinates: +X = right, +Y = down
    # Sky coordinates: +RA = East, +Dec = North
    
    # Vector from anchor 1 to anchor 2 in pixel space
    dx_px = p2[0] - p1[0]
    dy_px = p2[1] - p1[1]
    angle_px = math.atan2(dy_px, dx_px)  # Angle from +X axis (right)
    
    # Vector from anchor 1 to anchor 2 in sky space
    # Approximate for small fields: dRA points East, dDec points North
    dra = s2[0] - s1[0]
    ddec = s2[1] - s1[1]
    # Correct for cos(dec)
    dra_corrected = dra * math.cos(math.radians(dec_center))
    angle_sky = math.atan2(-ddec, dra_corrected)  # -ddec because North is up, but Dec increases up
    
    # Rotation needed to align image to sky
    # The image's "up" (+Y is down in image coords, so -Y is up) needs to point North
    rotation = math.degrees(angle_px - angle_sky)
    
    # Normalize to 0-360
    rotation = rotation % 360
    
    return (ra_center, dec_center, scale_degrees, rotation)

def main():
    # Paths
    base_path = Path(r"c:\Users\diego\cosmosmataro-skymap\app\src\main\assets\hevelius")
    index_path = base_path / "index.json"
    output_path = base_path / "art_metadata.json"
    
    # Load index.json
    with open(index_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    # Process each constellation
    results = []
    for const in data.get('constellations', []):
        const_id = const.get('id', 'unknown')
        name = const.get('common_name', {}).get('english', const_id)
        
        # Skip if no image data
        if 'image' not in const:
            continue
        
        image_data = const['image']
        file_path = image_data.get('file', '')
        size = image_data.get('size', [1024, 1024])
        anchors = image_data.get('anchors', [])
        
        if len(anchors) < 2:
            print(f"Skipping {name}: insufficient anchors")
            continue
        
        # Compute alignment
        result = compute_alignment(anchors, size)
        if result is None:
            print(f"Skipping {name}: could not compute alignment")
            continue
        
        ra, dec, scale, rotation = result
        
        print(f"{name}: RA={ra:.2f}, Dec={dec:.2f}, Scale={scale:.2f}°, Rot={rotation:.2f}°")
        
        results.append({
            "id": const_id,
            "name": name,
            "file": file_path,
            "ra": round(ra, 4),
            "dec": round(dec, 4),
            "scale": round(scale, 2),
            "rotation": round(rotation, 2)
        })
    
    # Write output
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2)
    
    print(f"\nGenerated {len(results)} entries in {output_path}")

if __name__ == "__main__":
    main()
