#!/usr/bin/env python3
"""
Verify star positions in Sky Map by properly decoding stars.binary
and comparing against known accurate catalog values.
"""

import sys
import os

# Add tools directory to path for the generated proto
sys.path.insert(0, os.path.dirname(__file__))

from source_pb2 import AstronomicalSourcesProto

# Known accurate star positions from Hipparcos/SIMBAD
# Format: Name -> (RA degrees, Dec degrees)
REFERENCE_STARS = {
    "betelgeuse": (88.7929, 7.4070),
    "rigel": (78.6346, -8.2016),
    "bellatrix": (81.2829, 6.3497),
    "saiph": (86.9391, -9.6696),
    "alnilam": (84.0533, -1.2019),
    "mintaka": (83.0017, -0.2992),
    "alnitak": (85.1897, -1.9426),
    "sirius": (101.2875, -16.7161),
    "procyon": (114.8254, 5.2250),
    "arcturus": (213.9150, 19.1825),
    "vega": (279.2346, 38.7837),
    "altair": (297.6958, 8.8683),
    "deneb": (310.3579, 45.2803),
    "capella": (79.1725, 45.9975),
    "aldebaran": (68.9800, 16.5092),
    "pollux": (116.3288, 28.0261),
    "castor": (113.6496, 31.8883),
    "polaris": (37.9546, 89.2641),
    "regulus": (152.0929, 11.9672),
    "spica": (201.2983, -11.1614),
    "antares": (247.3517, -26.4319),
    "fomalhaut": (344.4129, -29.6222),
}

def main():
    binary_path = r"c:\Users\diego\cosmosmataro-skymap\app\src\main\assets\stars.binary"
    
    with open(binary_path, 'rb') as f:
        data = f.read()
    
    sources = AstronomicalSourcesProto()
    sources.ParseFromString(data)
    
    print(f"Total sources in stars.binary: {len(sources.source)}")
    print("=" * 90)
    
    # Build a lookup of stars by name
    star_positions = {}
    for source in sources.source:
        # Get name from string IDs
        for name_id in source.name_str_ids:
            name_lower = name_id.lower().replace("_", " ")
            
            # Get position from search_location or first point
            if source.HasField('search_location'):
                ra = source.search_location.right_ascension
                dec = source.search_location.declination
            elif source.point:
                ra = source.point[0].location.right_ascension
                dec = source.point[0].location.declination
            else:
                continue
            
            star_positions[name_lower] = (ra, dec)
    
    print(f"Found {len(star_positions)} named stars")
    print()
    
    # Compare against reference
    matches = []
    for ref_name, (ref_ra, ref_dec) in REFERENCE_STARS.items():
        if ref_name in star_positions:
            sky_ra, sky_dec = star_positions[ref_name]
            ra_err = sky_ra - ref_ra
            dec_err = sky_dec - ref_dec
            matches.append((ref_name, sky_ra, sky_dec, ref_ra, ref_dec, ra_err, dec_err))
    
    # Sort by total error
    matches.sort(key=lambda x: abs(x[5]) + abs(x[6]), reverse=True)
    
    print("Star Position Comparison (sorted by error):")
    print("-" * 90)
    print(f"{'Star':<15} {'SkyMap RA':>10} {'SkyMap Dec':>10} | {'Catalog RA':>10} {'Catalog Dec':>10} | {'dRA':>8} {'dDec':>8}")
    print("-" * 90)
    
    total_ra_err = 0
    total_dec_err = 0
    for name, sky_ra, sky_dec, ref_ra, ref_dec, ra_err, dec_err in matches:
        print(f"{name:<15} {sky_ra:>10.4f} {sky_dec:>10.4f} | {ref_ra:>10.4f} {ref_dec:>10.4f} | {ra_err:>+8.4f} {dec_err:>+8.4f}")
        total_ra_err += abs(ra_err)
        total_dec_err += abs(dec_err)
    
    print("-" * 90)
    if matches:
        avg_ra_err = total_ra_err / len(matches)
        avg_dec_err = total_dec_err / len(matches)
        print(f"Average absolute errors: dRA = {avg_ra_err:.4f}°, dDec = {avg_dec_err:.4f}°")
        print(f"                        ({avg_ra_err * 60:.2f} arcmin, {avg_dec_err * 60:.2f} arcmin)")
    
    # List some sample stars that weren't matched
    print("\n\nSample of available star names in Sky Map:")
    for name in sorted(star_positions.keys())[:30]:
        print(f"  - {name}")

if __name__ == "__main__":
    main()
