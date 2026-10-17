# Where these sounds came from

Every `.ogg` in this directory is made from real recordings, cut, tuned, looped and levelled by `tools/soul_music/build_bank.py` (#163). Every recording is released under **CC0 1.0** (public domain dedication), so nothing here carries an attribution requirement. The credit below is given because it is owed, not because it is required. The instruments come from two libraries:

* **VSCO-2 Community Edition, Versilian Studios** - <https://github.com/sgossner/VSCO-2-CE>, commit `440300901dfe9275fd84e0b7763af1f8443ae62e`
* **Versilian Community Sample Library (VCSL), Versilian Studios and contributors** - <https://github.com/sgossner/VCSL>, commit `c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e`

and the foley from Freesound recordings, each checked as CC0 on its own page when it was fetched (`fetch_sources.py` refuses one that is not):

| Freesound | Author | Recording |
| --- | --- | --- |
| [103071](https://freesound.org/s/103071/) | alan3809 | Anvil Strikes_5.wav |
| [106114](https://freesound.org/s/106114/) | j1987 | bushmovement.wav |
| [117136](https://freesound.org/s/117136/) | cobratronik | Wind__Artic__Cold.wav |
| [166325](https://freesound.org/s/166325/) | deleted_user_2104797 | Water_drop_9.wav |
| [174718](https://freesound.org/s/174718/) | paespedro | Single Water Drop |
| [181252](https://freesound.org/s/181252/) | mario1298 | a gentle breeze, wind 3 |
| [233254](https://freesound.org/s/233254/) | alegemaate | Single Bird Chirp 2 |
| [233255](https://freesound.org/s/233255/) | alegemaate | Single Bird Chirp |
| [234782](https://freesound.org/s/234782/) | wubitog | Steam/hiss |
| [270588](https://freesound.org/s/270588/) | michorvath | Anvil Hit 2 |
| [270589](https://freesound.org/s/270589/) | michorvath | Anvil Hit 1 |
| [363169](https://freesound.org/s/363169/) | SamsterBirdies | Hammering on Anvil |
| [365071](https://freesound.org/s/365071/) | sniperous | Steam.wav |
| [380782](https://freesound.org/s/380782/) | BonnyOrbit | Clock ticks close to mic.wav |
| [398077](https://freesound.org/s/398077/) | SukritSen | Steam.wav |
| [414767](https://freesound.org/s/414767/) | samarobryn | Crackling Fire |
| [437356](https://freesound.org/s/437356/) | giddster | Rustling leaves |
| [443065](https://freesound.org/s/443065/) | AmberdeMeillon | Leaves_Rustling_Edited.wav |
| [521972](https://freesound.org/s/521972/) | Kastenfrosch | Blacksmith Hammer |
| [532191](https://freesound.org/s/532191/) | mcmikai | Fire in the stove |
| [812033](https://freesound.org/s/812033/) | qubodup | Excited Bird Chirp |

#209 asks that provenance be recorded because "CC0" and "CC-BY" are different promises. This bank makes the CC0 one, and it does so by construction: only CC0 libraries are read, each is pinned to a commit in `tools/soul_music/fetch_sources.py`, and every file says which recording it came from. A new source has to be CC0, and is recorded in both places before it is used.

## Rebuilding

```
python3 tools/soul_music/fetch_sources.py /path/to/sources
python3 tools/soul_music/build_bank.py --sources /path/to/sources
```

The second command rewrites `bank.json`, these files and this one. Run it on a clean tree and `git status` should show nothing; if it does not, the sources have moved or the scripts have, and that is worth finding out before a build ships. Needs numpy, scipy and soundfile, and an `ffmpeg` built with libvorbis - see `tools/soul_music/requirements.txt`.

## Format and levels

Pitches are scientific pitch with middle C = C4 = MIDI 60, whatever a library calls the same note in its file names (VSCO and VCSL do not agree with each other on that).

Mono, 44100 Hz, Ogg Vorbis at libvorbis quality 5, encoded through ffmpeg in bit-exact mode so a rebuild is byte-identical. The checksum is of the samples as 16-bit PCM, and it is the same rounding `mix.py` uses for its own checksums; it is not a checksum of the `.ogg` file.

Each instrument's `level` was first set so its stem, rendered by `MusicPreview` (`sh gradlew musicPreview -PpreviewArgs="--only stems --no-wav"`), measured what the oscillator it replaced measured, and then moved by ear. Each zone's `gain` brings its loudest 50 ms to one reference, so `level` is the one number per instrument a tuner changes. Do not edit `bank.json` or the `.ogg` files: change the script, and rebuild.

## Instruments

### pad

Strings: a cello section below middle C and a violin section above it, at their softest recorded dynamic. Sustained and crossfade-looped; the section's own vibrato is the movement the old pad's filter sweep stood in for.

`level` 0.135, sustain envelope, pitched, tone 1400-6000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `pad_00.ogg` | C2 (36) | -D2 | all | 2.333 | `75cc20711918e429…` | `VSCO-2-CE/Strings/Cello Section/susvib/susvib_C1_v1_1.wav` |
| `pad_01.ogg` | E2 (40) | D#2-F2 | all | 2.057 | `4e8235ddc61b5403…` | `VSCO-2-CE/Strings/Cello Section/susvib/susvib_E1_v1_1.wav` |
| `pad_02.ogg` | G2 (43) | F#2-A2 | all | 1.463 | `e2d678211a2986a4…` | `VSCO-2-CE/Strings/Cello Section/susvib/susvib_G1_v1_1.wav` |
| `pad_03.ogg` | B2 (47) | A#2-C3 | all | 1.542 | `0d959ee11d0ca582…` | `VSCO-2-CE/Strings/Cello Section/susvib/susvib_B1_v1_1.wav` |
| `pad_04.ogg` | D3 (50) | C#3-D#3 | all | 2.405 | `b94bff214f5c7a91…` | `VSCO-2-CE/Strings/Cello Section/susvib/susvib_D2_v1_1.wav` |
| `pad_05.ogg` | F3 (53) | E3-G3 | all | 2.120 | `49ab8881c970a839…` | `VSCO-2-CE/Strings/Cello Section/susvib/susvib_F2_v1_1.wav` |
| `pad_06.ogg` | A3 (57) | G#3-B3 | all | 1.782 | `fd31aba22cd02981…` | `VSCO-2-CE/Strings/Cello Section/susvib/susvib_A2_v1_1.wav` |
| `pad_07.ogg` | D4 (62) | C4-E4 | all | 2.417 | `9b92159658aea369…` | `VSCO-2-CE/Strings/Violin Section/susVib/VlnEns_susVib_D3_v1.wav` |
| `pad_08.ogg` | F#4 (66) | F4-G4 | all | 2.405 | `f1e33f1de904f0fb…` | `VSCO-2-CE/Strings/Violin Section/susVib/VlnEns_susVib_F#3_v1.wav` |
| `pad_09.ogg` | A4 (69) | G#4-A#4 | all | 3.439 | `13df0b5239187ecb…` | `VSCO-2-CE/Strings/Violin Section/susVib/VlnEns_susVib_A3_v1.wav` |
| `pad_10.ogg` | C5 (72) | B4-D5 | all | 2.199 | `79123d35d5133165…` | `VSCO-2-CE/Strings/Violin Section/susVib/VlnEns_susVib_C4_v1.wav` |
| `pad_11.ogg` | E5 (76) | D#5+ | all | 2.736 | `57160b280e908fa2…` | `VSCO-2-CE/Strings/Violin Section/susVib/VlnEns_susVib_E4_v1.wav` |

### epiano

Felt-soft upright piano, two velocity layers, so a soft note is a different strike rather than a quieter copy of a hard one.

`level` 0.274, struck envelope, pitched, tone 1800-9000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `epiano_00.ogg` | A2 (45) | -B2 | 0.0-0.5 | 1.766 | `f0ebe649df51092b…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_012.wav` |
| `epiano_01.ogg` | A2 (45) | -B2 | 0.5-1.0 | 1.957 | `d27ab6cf2556f441…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_012.wav` |
| `epiano_02.ogg` | C#3 (49) | C3-D#3 | 0.0-0.5 | 1.482 | `710e6ace37899b5c…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_014.wav` |
| `epiano_03.ogg` | C#3 (49) | C3-D#3 | 0.5-1.0 | 1.735 | `6f59eef1a430184e…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_014.wav` |
| `epiano_04.ogg` | F3 (53) | E3-G3 | 0.0-0.5 | 1.795 | `9ff36e0aee020e41…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_016.wav` |
| `epiano_05.ogg` | F3 (53) | E3-G3 | 0.5-1.0 | 1.726 | `737a04847ffcac72…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_016.wav` |
| `epiano_06.ogg` | A3 (57) | G#3-B3 | 0.0-0.5 | 2.206 | `c10fb8270eef941c…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_018.wav` |
| `epiano_07.ogg` | A3 (57) | G#3-B3 | 0.5-1.0 | 2.497 | `c8453e1af1b695b7…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_018.wav` |
| `epiano_08.ogg` | C#4 (61) | C4-D#4 | 0.0-0.5 | 2.002 | `d9694450f4393456…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_020.wav` |
| `epiano_09.ogg` | C#4 (61) | C4-D#4 | 0.5-1.0 | 2.230 | `81443bbeec09d8f4…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_020.wav` |
| `epiano_10.ogg` | F4 (65) | E4-G4 | 0.0-0.5 | 1.349 | `64855d16a53e47d0…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_022.wav` |
| `epiano_11.ogg` | F4 (65) | E4-G4 | 0.5-1.0 | 1.545 | `81ddd14cc4fa718c…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_022.wav` |
| `epiano_12.ogg` | A4 (69) | G#4-B4 | 0.0-0.5 | 1.217 | `088896b2f373eb14…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_024.wav` |
| `epiano_13.ogg` | A4 (69) | G#4-B4 | 0.5-1.0 | 1.287 | `e208f40bfe393949…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_024.wav` |
| `epiano_14.ogg` | C#5 (73) | C5-D#5 | 0.0-0.5 | 1.666 | `e71284c6c3b577bc…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_026.wav` |
| `epiano_15.ogg` | C#5 (73) | C5-D#5 | 0.5-1.0 | 1.780 | `3b8f9f2c39a1c17b…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_026.wav` |
| `epiano_16.ogg` | F5 (77) | E5-G5 | 0.0-0.5 | 2.906 | `a73355a6898172b4…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_028.wav` |
| `epiano_17.ogg` | F5 (77) | E5-G5 | 0.5-1.0 | 2.699 | `9aef56ac2fc15e7d…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_028.wav` |
| `epiano_18.ogg` | A5 (81) | G#5+ | 0.0-0.5 | 1.980 | `55d16fdd546c3ec1…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn1_rr1_030.wav` |
| `epiano_19.ogg` | A5 (81) | G#5+ | 0.5-1.0 | 2.430 | `b8a8b4a80dd5352f…` | `VSCO-2-CE/Keys/Upright Piano/Player_dyn2_rr1_030.wav` |

### bell

Clarinet long tones at its softest dynamic, looped: the bell's part with nothing struck, because a lead line of chimes dinged.

`level` 0.284, sustain envelope, pitched, tone 1500-7000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `bell_00.ogg` | D3 (50) | -D#3 | all | 1.976 | `f6e915e44de05b55…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_D2_v1_rr1_sum.wav` |
| `bell_01.ogg` | F3 (53) | E3-G3 | all | 2.141 | `374c76ed81f3f615…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_F2_v1_rr1_sum.wav` |
| `bell_02.ogg` | A#3 (58) | G#3-C4 | all | 1.492 | `19064901b0a0deb2…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_A#2_v1_rr1_sum.wav` |
| `bell_03.ogg` | D4 (62) | C#4-D#4 | all | 1.368 | `4851f3d0c095184f…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_D3_v1_rr1_sum.wav` |
| `bell_04.ogg` | F4 (65) | E4-G4 | all | 1.499 | `139de489545e915b…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_F3_v1_rr1_sum.wav` |
| `bell_05.ogg` | A#4 (70) | G#4-C5 | all | 1.218 | `b97a3c14725fe66c…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_A#3_v1_rr1_sum.wav` |
| `bell_06.ogg` | D5 (74) | C#5-D#5 | all | 1.445 | `e4135bcad8737d1b…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_D4_v1_rr1_sum.wav` |
| `bell_07.ogg` | F5 (77) | E5+ | all | 1.748 | `1200c83f9182d058…` | `VSCO-2-CE/Woodwinds/Clarinet/susLong/DCClar_susLong_F4_v1_rr1_sum.wav` |

### glass

A bowed vibraphone for held notes, and the soft-mallet vibraphone, 4 dB down, only for quick ones.

`level` 0.267, sustain envelope, pitched, tone 1600-5000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `glass_00.ogg` | F3 (53) | -G3 | all | 0.879 | `b271be76eaf14684…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_F2_v2_rr1_Main.wav` |
| `glass_01.ogg` | A3 (57) | G#3-A#3 | all | 0.863 | `e8f36b8abddbb123…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_A2_v2_rr1_Main.wav` |
| `glass_02.ogg` | A3 (57) | -C4 | all | 1.581 | `aaa38272ffb13dab…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Bowed/Vibes_bowed_A2_rr1_Main.wav` |
| `glass_03.ogg` | C4 (60) | B3-D4 | all | 1.119 | `3e13e13f6136e670…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_C3_v2_rr1_Main.wav` |
| `glass_04.ogg` | E4 (64) | D#4-F4 | all | 0.861 | `d22da6c246306c5d…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_E3_v2_rr1_Main.wav` |
| `glass_05.ogg` | E4 (64) | C#4-F4 | all | 1.413 | `c4a9b31deee76980…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Bowed/Vibes_bowed_E3_rr1_Main.wav` |
| `glass_06.ogg` | G4 (67) | F#4-A4 | all | 0.811 | `82c5aee09afeab32…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_G3_v2_rr1_Main.wav` |
| `glass_07.ogg` | G4 (67) | F#4-A#4 | all | 1.443 | `55355dfdda1e3529…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Bowed/Vibes_bowed_G3_rr1_Main.wav` |
| `glass_08.ogg` | B4 (71) | A#4-C5 | all | 0.927 | `528a5d385697c70b…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_B3_v2_rr1_Main.wav` |
| `glass_09.ogg` | D5 (74) | C#5-D#5 | all | 0.869 | `a78bd18c62e01dc4…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_D4_v2_rr1_Main.wav` |
| `glass_10.ogg` | D5 (74) | B4-F5 | all | 1.443 | `2e8bb8935cb4893b…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Bowed/Vibes_bowed_D4_rr1_Main.wav` |
| `glass_11.ogg` | F5 (77) | E5+ | all | 0.768 | `2f02f25956f9940b…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Soft Mallets/Vibes_soft_F4_v2_rr1_Main.wav` |
| `glass_12.ogg` | A5 (81) | F#5-C6 | all | 1.432 | `4751098231db357b…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Bowed/Vibes_bowed_A4_rr1_Main.wav` |
| `glass_13.ogg` | E6 (88) | C#6+ | all | 1.227 | `81ed7e838d6dcbb2…` | `VCSL/Idiophones/Struck Idiophones/Vibraphone/Bowed/Vibes_bowed_E5_rr1_Main.wav` |

### pluck

Concert harp.

`level` 0.312, struck envelope, pitched, tone 1500-8000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `pluck_00.ogg` | G1 (31) | -A#1 | all | 2.373 | `ab992e9a2620e3d6…` | `VSCO-2-CE/Strings/Harp/KSHarp_G1_mp.wav` |
| `pluck_01.ogg` | D2 (38) | B1-D#2 | all | 1.459 | `cec8dcebc68544ed…` | `VSCO-2-CE/Strings/Harp/KSHarp_D2_mf.wav` |
| `pluck_02.ogg` | F2 (41) | E2-G2 | all | 2.030 | `2143835a263ff7a2…` | `VSCO-2-CE/Strings/Harp/KSHarp_F2_mf.wav` |
| `pluck_03.ogg` | A2 (45) | G#2-A#2 | all | 1.679 | `120a982fb34ee155…` | `VSCO-2-CE/Strings/Harp/KSHarp_A2_mf.wav` |
| `pluck_04.ogg` | C3 (48) | B2-D3 | all | 1.526 | `4df393a6400bb02a…` | `VSCO-2-CE/Strings/Harp/KSHarp_C3_mf.wav` |
| `pluck_05.ogg` | E3 (52) | D#3-F3 | all | 2.131 | `52756f4444eeea37…` | `VSCO-2-CE/Strings/Harp/KSHarp_E3_mf.wav` |
| `pluck_06.ogg` | G3 (55) | F#3-A3 | all | 1.644 | `9ee0043a8f5945b1…` | `VSCO-2-CE/Strings/Harp/KSHarp_G3_mf.wav` |
| `pluck_07.ogg` | B3 (59) | A#3-C4 | all | 1.699 | `99613fb9d8124e66…` | `VSCO-2-CE/Strings/Harp/KSHarp_B3_mf.wav` |
| `pluck_08.ogg` | D4 (62) | C#4-D#4 | all | 1.766 | `c9b9ceed56e4023b…` | `VSCO-2-CE/Strings/Harp/KSHarp_D4_mf.wav` |
| `pluck_09.ogg` | F4 (65) | E4-G4 | all | 1.395 | `b26fd7708a389d32…` | `VSCO-2-CE/Strings/Harp/KSHarp_F4_mf.wav` |
| `pluck_10.ogg` | A4 (69) | G#4-A#4 | all | 2.164 | `a02fd20cf607080a…` | `VSCO-2-CE/Strings/Harp/KSHarp_A4_mf.wav` |
| `pluck_11.ogg` | C5 (72) | B4-D5 | all | 2.272 | `eb40502fdadf3cf8…` | `VSCO-2-CE/Strings/Harp/KSHarp_C5_mf.wav` |
| `pluck_12.ogg` | E5 (76) | D#5+ | all | 1.812 | `eeba18fc3c2f04ce…` | `VSCO-2-CE/Strings/Harp/KSHarp_E5_mf.wav` |

### mallet

Marimba, medium strikes.

`level` 0.574, struck envelope, pitched, tone 1600-8000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `mallet_00.ogg` | F2 (41) | -G#2 | all | 1.497 | `69ecdcf9af6c7122…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_F1_med_01.wav` |
| `mallet_01.ogg` | C3 (48) | A2-D#3 | all | 1.598 | `fb896588ee8e1ec2…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_C2_med_01.wav` |
| `mallet_02.ogg` | G3 (55) | E3-A3 | all | 1.305 | `d20b1c8fc8d46aa4…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_G2_med_01.wav` |
| `mallet_03.ogg` | B3 (59) | A#3-D4 | all | 1.350 | `63076a5367743fbc…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_B2_med_01.wav` |
| `mallet_04.ogg` | F4 (65) | D#4-G#4 | all | 1.231 | `4345bda49adf4150…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_F3_med_01.wav` |
| `mallet_05.ogg` | C5 (72) | A4-D#5 | all | 1.484 | `b008d6eab5aecfa1…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_C4_med_01.wav` |
| `mallet_06.ogg` | G5 (79) | E5-A5 | all | 1.486 | `3c9c8d40e3010732…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_G4_med_01.wav` |
| `mallet_07.ogg` | B5 (83) | A#5+ | all | 1.334 | `29a04eb23abd8239…` | `VCSL/Idiophones/Struck Idiophones/Marimba/Marimba_hit_Outrigger_B4_med_01.wav` |

### drone

Quiet organ pedal flue, looped: a note held for as long as a whole piece needs.

`level` 0.21, sustain envelope, pitched, tone 700-2400 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `drone_00.ogg` | C2 (36) | -C#2 | all | 1.523 | `c1a90e1306eea852…` | `VSCO-2-CE/Keys/Organ/Quiet/NT5_PedalQuiet_064_rr1.wav` |
| `drone_01.ogg` | D#2 (39) | D2-E2 | all | 2.010 | `bbcf11e260a9a8ce…` | `VSCO-2-CE/Keys/Organ/Quiet/NT5_PedalQuiet_067_rr1.wav` |
| `drone_02.ogg` | F#2 (42) | F2-G2 | all | 1.722 | `b5dfadd38f183689…` | `VSCO-2-CE/Keys/Organ/Quiet/NT5_PedalQuiet_070_rr1.wav` |
| `drone_03.ogg` | A2 (45) | G#2+ | all | 1.662 | `2563e661ce6b8264…` | `VSCO-2-CE/Keys/Organ/Quiet/NT5_PedalQuiet_073_rr1.wav` |

### flute

Flute without vibrato, looped, so a held note holds still.

`level` 0.185, sustain envelope, pitched, tone 2000-9000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `flute_00.ogg` | C4 (60) | -D4 | all | 1.889 | `b64608415a35741c…` | `VSCO-2-CE/Woodwinds/Flute/susNV/LDFlute_susNV_C3_v1_1.wav` |
| `flute_01.ogg` | E4 (64) | D#4-F#4 | all | 1.532 | `7ebfa1e77155ffe2…` | `VSCO-2-CE/Woodwinds/Flute/susNV/LDFlute_susNV_E3_v1_1.wav` |
| `flute_02.ogg` | A4 (69) | G4-A#4 | all | 1.789 | `d66cdebad469a7c0…` | `VSCO-2-CE/Woodwinds/Flute/susNV/LDFlute_susNV_A3_v1_1.wav` |
| `flute_03.ogg` | C5 (72) | B4-D5 | all | 1.579 | `89ad6133faa4c5fc…` | `VSCO-2-CE/Woodwinds/Flute/susNV/LDFlute_susNV_C4_v1_1.wav` |
| `flute_04.ogg` | E5 (76) | D#5-F#5 | all | 1.800 | `324e01988ed0bc40…` | `VSCO-2-CE/Woodwinds/Flute/susNV/LDFlute_susNV_E4_v1_1.wav` |
| `flute_05.ogg` | A5 (81) | G5+ | all | 1.616 | `033962433cfdd382…` | `VSCO-2-CE/Woodwinds/Flute/susNV/LDFlute_susNV_A4_v1_1.wav` |

### thump

Foley: a frame drum, muted, round-robin.

`level` 0.4, struck envelope, unpitched, tone 500-4000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `thump_00.ogg` | C3 (48) | all | all | 2.094 | `249b95993781bd7f…` | `VCSL/Membranophones/Struck Membranophones/Frame Drum/HDrumL_HitMuted_v2_rr1_Sum.wav` |
| `thump_01.ogg` | C3 (48) | all | all | 2.380 | `f5ac388c2655b722…` | `VCSL/Membranophones/Struck Membranophones/Frame Drum/HDrumL_HitMuted_v2_rr2_Sum.wav` |
| `thump_02.ogg` | C3 (48) | all | all | 3.641 | `c76ed14b575b1232…` | `VCSL/Membranophones/Struck Membranophones/Frame Drum/HDrumL_HitMuted_v3_rr1_Sum.wav` |
| `thump_03.ogg` | C3 (48) | all | all | 3.291 | `3ac81bf223b9333a…` | `VCSL/Membranophones/Struck Membranophones/Frame Drum/HDrumL_HitMuted_v3_rr2_Sum.wav` |

### clank

Foley: a hammer on an anvil, recorded by smiths on Freesound, round-robin.

`level` 0.665, struck envelope, unpitched, tone 2000-6000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `clank_00.ogg` | C5 (72) | all | all | 1.478 | `6da5db6d41a3b020…` | Freesound [270588](https://freesound.org/s/270588/) by michorvath |
| `clank_01.ogg` | C5 (72) | all | all | 2.496 | `bbb5deecb604f722…` | Freesound [270589](https://freesound.org/s/270589/) by michorvath |
| `clank_02.ogg` | C5 (72) | all | all | 6.186 | `438f81259e18786f…` | Freesound [521972](https://freesound.org/s/521972/) by Kastenfrosch |
| `clank_03.ogg` | C5 (72) | all | all | 1.014 | `332e9782ab29ffb2…` | Freesound [363169](https://freesound.org/s/363169/) by SamsterBirdies |
| `clank_04.ogg` | C5 (72) | all | all | 1.024 | `83aabedcdc748fec…` | Freesound [363169](https://freesound.org/s/363169/) by SamsterBirdies |
| `clank_05.ogg` | C5 (72) | all | all | 3.807 | `3d3b9a2d45652329…` | Freesound [103071](https://freesound.org/s/103071/) by alan3809 |

### tick

Foley: a clock's own ticks, cut from a close-miked take, round-robin.

`level` 0.27, struck envelope, unpitched, tone 2000-8000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `tick_00.ogg` | C6 (84) | all | all | 2.814 | `965134cfa082600e…` | Freesound [380782](https://freesound.org/s/380782/) by BonnyOrbit |
| `tick_01.ogg` | C6 (84) | all | all | 2.593 | `3bd3653f67a8266e…` | Freesound [380782](https://freesound.org/s/380782/) by BonnyOrbit |
| `tick_02.ogg` | C6 (84) | all | all | 2.619 | `686a2cf208281c0b…` | Freesound [380782](https://freesound.org/s/380782/) by BonnyOrbit |
| `tick_03.ogg` | C6 (84) | all | all | 2.807 | `8c9b3195977e9dd3…` | Freesound [380782](https://freesound.org/s/380782/) by BonnyOrbit |

### hiss

Foley: bursts of steam, rolled off above 6 kHz so a vent breathes rather than sizzles.

`level` 0.986, swell envelope, unpitched, tone 2000-4500 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `hiss_00.ogg` | C5 (72) | all | all | 3.238 | `14122029f03ff94e…` | Freesound [365071](https://freesound.org/s/365071/) by sniperous |
| `hiss_01.ogg` | C5 (72) | all | all | 5.303 | `2ebffd2fd43ec663…` | Freesound [234782](https://freesound.org/s/234782/) by wubitog |
| `hiss_02.ogg` | C5 (72) | all | all | 3.161 | `7af50a6d2ec8c909…` | Freesound [398077](https://freesound.org/s/398077/) by SukritSen |

### wind

Foley: arctic wind and a gentle breeze, low-passed and looped.

`level` 0.347, swell envelope, unpitched, tone 700-2600 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `wind_00.ogg` | C4 (60) | all | all | 3.152 | `2e14a17adcc44c93…` | Freesound [117136](https://freesound.org/s/117136/) by cobratronik |
| `wind_01.ogg` | C4 (60) | all | all | 2.077 | `7faaf5075013ca4c…` | Freesound [181252](https://freesound.org/s/181252/) by mario1298 |

### drip

Foley: single water drops.

`level` 0.296, struck envelope, unpitched.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `drip_00.ogg` | E5 (76) | all | all | 3.512 | `55dc944ba6363d61…` | Freesound [174718](https://freesound.org/s/174718/) by paespedro |
| `drip_01.ogg` | E5 (76) | all | all | 4.531 | `567f3000f51ea136…` | Freesound [166325](https://freesound.org/s/166325/) by deleted_user_2104797 |

### chirp

Single bird chirps.

`level` 0.207, struck envelope, unpitched.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `chirp_00.ogg` | E6 (88) | all | all | 2.652 | `b0c0fe8a861ced09…` | Freesound [233255](https://freesound.org/s/233255/) by alegemaate |
| `chirp_01.ogg` | E6 (88) | all | all | 8.283 | `96722ae698507cf1…` | Freesound [233254](https://freesound.org/s/233254/) by alegemaate |
| `chirp_02.ogg` | E6 (88) | all | all | 3.055 | `7f37c2f07e5ba021…` | Freesound [812033](https://freesound.org/s/812033/) by qubodup |

### rustle

Foley: rustling leaves.

`level` 0.408, swell envelope, unpitched, tone 1200-6000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `rustle_00.ogg` | C5 (72) | all | all | 4.407 | `01ca544b155fcf1b…` | Freesound [437356](https://freesound.org/s/437356/) by giddster |
| `rustle_01.ogg` | C5 (72) | all | all | 5.234 | `6a9dd2f4dd08de58…` | Freesound [443065](https://freesound.org/s/443065/) by AmberdeMeillon |
| `rustle_02.ogg` | C5 (72) | all | all | 7.984 | `3c2aa23b1ff20538…` | Freesound [106114](https://freesound.org/s/106114/) by j1987 |

### swell

Foley: a suspended cymbal roll, looped.

`level` 0.35, swell envelope, unpitched, tone 1500-7000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `swell_00.ogg` | C4 (60) | all | all | 2.151 | `a82b0422d737a2cd…` | `VCSL/Idiophones/Struck Idiophones/Suspended Cymbal 1/susCymb1_roll_mp1_nloop.wav` |

### crackle

A real fire: a wood stove close by with a second fire under it, one looped bed and three of its own pops.

`level` 0.17, sustain envelope, unpitched, tone 1500-5000 Hz.

| File | Root | Keys | Velocity | Gain | PCM sha256 | Source recording |
| --- | --- | --- | --- | --- | --- | --- |
| `crackle_00.ogg` | C4 (60) | all | all | 8.960 | `ebce3333855d5326…` | Freesound [414767](https://freesound.org/s/414767/) by samarobryn |
| `crackle_01.ogg` | C4 (60) | all | all | 9.380 | `63cf832d3e099c9e…` | Freesound [414767](https://freesound.org/s/414767/) by samarobryn |
| `crackle_02.ogg` | C4 (60) | all | all | 12.193 | `044465899d15c840…` | Freesound [414767](https://freesound.org/s/414767/) by samarobryn |
| `crackle_03.ogg` | C4 (60) | all | all | 29.252 | `1fe1b9a1315e5602…` | Freesound [532191](https://freesound.org/s/532191/) by mcmikai; Freesound [414767](https://freesound.org/s/414767/) by samarobryn |


_Written by `build_bank.py`; see the note on rebuilding above._
