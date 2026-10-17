# Where these sounds came from

Every file in this directory is a mix of real recordings, made by `tools/ambience/mix.py`
(#163). Every recording is released under **CC0 1.0** (public domain dedication), so
nothing here carries an attribution requirement - the credit below is given because it is
owed, not because it is required. Instruments come from two libraries:

* **VSCO-2 Community Edition**, Versilian Studios -
  <https://github.com/sgossner/VSCO-2-CE>, commit `440300901dfe9275fd84e0b7763af1f8443ae62e`
* **Versilian Community Sample Library (VCSL)**, Versilian Studios and contributors -
  <https://github.com/sgossner/VCSL>, commit `c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e`

and the field recordings and sound effects from Freesound, each one checked as CC0 on its
own page when it was fetched (`fetch_sources.py` refuses any that is not):

| Freesound | Author | Recording |
| --- | --- | --- |
| [9523](https://freesound.org/s/9523/) | burnttoys | cymbal-reversed-glassy-resonance.wav |
| [32655](https://freesound.org/s/32655/) | greysound | FrogsAndCrickets_ExcerptB_JMA_24Bit_48k.wav |
| [106114](https://freesound.org/s/106114/) | j1987 | bushmovement.wav |
| [110218](https://freesound.org/s/110218/) | genocidalguitar | reverse crash.wav |
| [117136](https://freesound.org/s/117136/) | cobratronik | Wind__Artic__Cold.wav |
| [137836](https://freesound.org/s/137836/) | thefilmbakery | Blacksmith Workshop.WAV |
| [165526](https://freesound.org/s/165526/) | felix.blume | Wind singing in the mountain, some night cricket in background |
| [166325](https://freesound.org/s/166325/) | deleted_user_2104797 | Water_drop_9.wav |
| [174718](https://freesound.org/s/174718/) | paespedro | Single Water Drop |
| [177958](https://freesound.org/s/177958/) | Sclolex | Water Dripping in Cave.wav |
| [181252](https://freesound.org/s/181252/) | mario1298 | a gentle breeze, wind 3 |
| [233254](https://freesound.org/s/233254/) | alegemaate | Single Bird Chirp 2 |
| [233255](https://freesound.org/s/233255/) | alegemaate | Single Bird Chirp |
| [234782](https://freesound.org/s/234782/) | wubitog | Steam/hiss |
| [270588](https://freesound.org/s/270588/) | michorvath | Anvil Hit 2 |
| [270589](https://freesound.org/s/270589/) | michorvath | Anvil Hit 1 |
| [329744](https://freesound.org/s/329744/) | humanoide9000 | Ice break |
| [333221](https://freesound.org/s/333221/) | hdfreema | Night Crickets Back Porch.aiff |
| [342546](https://freesound.org/s/342546/) | timbreknight | Ice cracking |
| [353311](https://freesound.org/s/353311/) | J.Zazvurek | Early summer, czech wood outside the camp, early morning ambiance |
| [365071](https://freesound.org/s/365071/) | sniperous | Steam.wav |
| [377552](https://freesound.org/s/377552/) | Mafon2 | Wooden Creak |
| [383903](https://freesound.org/s/383903/) | deleted_user_7146007 | Reverse Crash Cymbal Projection |
| [398077](https://freesound.org/s/398077/) | SukritSen | Steam.wav |
| [398684](https://freesound.org/s/398684/) | Dominik_W | Water boiler |
| [414260](https://freesound.org/s/414260/) | tenkism | windingOldAlarmClock.wav |
| [414762](https://freesound.org/s/414762/) | tammarpokk | Bubbling Hot Spring |
| [414767](https://freesound.org/s/414767/) | samarobryn | Crackling Fire |
| [422623](https://freesound.org/s/422623/) | Aurelon | Crack and Crunch |
| [437356](https://freesound.org/s/437356/) | giddster | Rustling leaves |
| [443065](https://freesound.org/s/443065/) | AmberdeMeillon | Leaves_Rustling_Edited.wav |
| [502507](https://freesound.org/s/502507/) | Rudmer_Rotteveel | Creaking Wood 4 Steps |
| [521972](https://freesound.org/s/521972/) | Kastenfrosch | Blacksmith Hammer |
| [530161](https://freesound.org/s/530161/) | Flamiffer | Dungeon Air |
| [532191](https://freesound.org/s/532191/) | mcmikai | Fire in the stove |
| [533146](https://freesound.org/s/533146/) | mattfinarelli | Drop - Water |
| [586150](https://freesound.org/s/586150/) | ChemiCatz | clockwork.wav |
| [657255](https://freesound.org/s/657255/) | Tom_Kaszuba | Old Clock Pendulum |
| [734580](https://freesound.org/s/734580/) | horraceradishh | Single Medium-Length Wind-up |
| [812033](https://freesound.org/s/812033/) | qubodup | Excited Bird Chirp |
| [828434](https://freesound.org/s/828434/) | Saladsounds | Sudden Burst of Small Flame |

#209 asks that provenance be recorded because "CC0" and "CC-BY" are different promises.
This set makes the CC0 one. The mixes themselves (the choice of recordings, edits,
placement, room and levels) are the mod's own, under its licence. To check or rebuild:

```
python3 tools/soul_music/fetch_sources.py /path/to/sources
python3 tools/ambience/mix.py --sources /path/to/sources
```

Mono, 44100 Hz, Ogg Vorbis at libvorbis quality 5. The checksum is of the 16-bit PCM
handed to the encoder, not of the `.ogg` container.

| File | What it is | Length | RMS | Peak | PCM sha256 |
| --- | --- | --- | --- | --- | --- |
| `base_1.ogg` | one-shot, base, variant 1: a gust of mountain wind (Freesound) | 4.50s | -20.0 dB | -5.1 dB | `330391dd747a13b0…` |
| `base_2.ogg` | one-shot, base, variant 2: a gust of mountain wind (Freesound) | 4.50s | -20.0 dB | -3.0 dB | `c8c52db095f9c771…` |
| `base_3.ogg` | one-shot, base, variant 3: a gust of mountain wind (Freesound) | 4.50s | -20.0 dB | -7.0 dB | `983598064cc61f3d…` |
| `warm_1.ogg` | one-shot, warm, variant 1: a flame bursting, or a pop out of a real fire (Freesound) | 3.00s | -20.2 dB | -3.0 dB | `9855cbf12c7925e5…` |
| `warm_2.ogg` | one-shot, warm, variant 2: a flame bursting, or a pop out of a real fire (Freesound) | 3.00s | -20.5 dB | -3.0 dB | `f15a2f7f75641d75…` |
| `warm_3.ogg` | one-shot, warm, variant 3: a flame bursting, or a pop out of a real fire (Freesound) | 3.00s | -20.2 dB | -3.0 dB | `0203c0d6c229d087…` |
| `cold_1.ogg` | one-shot, cold, variant 1: ice cracking (Freesound) | 4.00s | -20.2 dB | -3.0 dB | `da11ffb0832e1cff…` |
| `cold_2.ogg` | one-shot, cold, variant 2: ice cracking (Freesound) | 4.00s | -20.2 dB | -3.0 dB | `8e7142843b96f5b9…` |
| `cold_3.ogg` | one-shot, cold, variant 3: ice cracking (Freesound) | 4.00s | -20.1 dB | -3.0 dB | `4033f862eaf10f49…` |
| `steam_1.ogg` | one-shot, steam, variant 1: a burst of steam (Freesound) | 3.40s | -20.0 dB | -3.0 dB | `498c6744777820b4…` |
| `steam_2.ogg` | one-shot, steam, variant 2: a burst of steam (Freesound) | 3.40s | -20.2 dB | -3.0 dB | `28189aa9f0d173d5…` |
| `steam_3.ogg` | one-shot, steam, variant 3: a burst of steam (Freesound) | 3.40s | -20.2 dB | -3.0 dB | `987ab561ae4f1a23…` |
| `arcane_1.ogg` | one-shot, arcane, variant 1: a reversed cymbal (Freesound) into a soft harp figure (VSCO-2) | 4.20s | -20.0 dB | -5.3 dB | `db6bc55844064505…` |
| `arcane_2.ogg` | one-shot, arcane, variant 2: a reversed cymbal (Freesound) into a soft harp figure (VSCO-2) | 4.20s | -20.0 dB | -5.4 dB | `8fd93999f4a67416…` |
| `arcane_3.ogg` | one-shot, arcane, variant 3: a reversed cymbal (Freesound) into a soft harp figure (VSCO-2) | 4.20s | -19.9 dB | -3.0 dB | `567f33f076d42350…` |
| `wrought_1.ogg` | one-shot, wrought, variant 1: a hammer on an anvil (Freesound) over a lowpassed timpani (VCSL) | 3.20s | -20.1 dB | -3.0 dB | `4b7e6af139e31f70…` |
| `wrought_2.ogg` | one-shot, wrought, variant 2: a hammer on an anvil (Freesound) over a lowpassed timpani (VCSL) | 3.20s | -20.1 dB | -3.0 dB | `6312077c412cab34…` |
| `wrought_3.ogg` | one-shot, wrought, variant 3: a hammer on an anvil (Freesound) over a lowpassed timpani (VCSL) | 3.20s | -20.3 dB | -3.0 dB | `b768f6b7989e451f…` |
| `quickened_1.ogg` | one-shot, quickened, variant 1: a clockwork mechanism being wound (Freesound) | 4.00s | -20.5 dB | -3.0 dB | `60900a73d126cf3e…` |
| `quickened_2.ogg` | one-shot, quickened, variant 2: a clockwork mechanism being wound (Freesound) | 4.00s | -20.3 dB | -3.0 dB | `6969b619406bbeb7…` |
| `quickened_3.ogg` | one-shot, quickened, variant 3: a clockwork mechanism being wound (Freesound) | 4.00s | -20.6 dB | -3.0 dB | `1a8526b41f1ad3b7…` |
| `verdant_1.ogg` | one-shot, verdant, variant 1: a bird chirp and rustling leaves (Freesound) | 3.40s | -20.1 dB | -3.0 dB | `3046379182e3ce69…` |
| `verdant_2.ogg` | one-shot, verdant, variant 2: a bird chirp and rustling leaves (Freesound) | 3.40s | -20.2 dB | -3.0 dB | `0cf6be852000861b…` |
| `verdant_3.ogg` | one-shot, verdant, variant 3: a bird chirp and rustling leaves (Freesound) | 3.40s | -20.1 dB | -3.0 dB | `67cc18b39aded0ac…` |
| `hollow_1.ogg` | one-shot, hollow, variant 1: creaking wood far off (Freesound) and a soft bass drum (VCSL) | 4.20s | -20.1 dB | -3.0 dB | `1a1709578ede4e59…` |
| `hollow_2.ogg` | one-shot, hollow, variant 2: creaking wood far off (Freesound) and a soft bass drum (VCSL) | 4.20s | -20.0 dB | -3.1 dB | `94fd8af8dbe6930d…` |
| `hollow_3.ogg` | one-shot, hollow, variant 3: creaking wood far off (Freesound) and a soft bass drum (VCSL) | 4.20s | -20.0 dB | -3.0 dB | `33b5010af0a3a5a1…` |
| `overgrown_1.ogg` | one-shot, overgrown, variant 1: rustling leaves and a water drop (Freesound) | 4.00s | -20.1 dB | -3.0 dB | `cadfbe176e532419…` |
| `overgrown_2.ogg` | one-shot, overgrown, variant 2: rustling leaves and a water drop (Freesound) | 4.00s | -20.2 dB | -3.0 dB | `574aeadd6e79a9a3…` |
| `overgrown_3.ogg` | one-shot, overgrown, variant 3: rustling leaves and a water drop (Freesound) | 4.00s | -20.1 dB | -3.0 dB | `8d7d5410f6739b0c…` |
| `bed_close.ogg` | rank bed, close: a gentle breeze, darkened, with crickets far off (Freesound), seamless loop | 68.00s | -24.1 dB | -8.0 dB | `fa9b4d565e93571a…` |
| `bed_open.ogg` | rank bed, open: wind singing across a mountain top at night (Freesound), seamless loop | 68.00s | -24.0 dB | -9.5 dB | `f8dacf1824d75288…` |
| `character_warm.ogg` | character layer, warm: a wood stove close by and a second fire further off (Freesound) | 68.00s | -28.2 dB | -8.0 dB | `24a48f2e6c88dbb2…` |
| `character_cold.ogg` | character layer, cold: arctic wind (Freesound) | 68.00s | -28.0 dB | -12.6 dB | `82f02077d4265091…` |
| `character_steam.ogg` | character layer, steam: a bubbling hot spring over a water boiler (Freesound) | 68.00s | -28.0 dB | -8.0 dB | `ae9c48aa347bb905…` |
| `character_arcane.ogg` | character layer, arcane: dungeon air with the occasional reversed cymbal swell (Freesound) | 68.00s | -28.0 dB | -8.0 dB | `641797142d11ff1a…` |
| `character_wrought.ogg` | character layer, wrought: a blacksmith's workshop (Freesound) | 68.00s | -28.1 dB | -8.0 dB | `f2698a677f1b3ae9…` |
| `character_quickened.ogg` | character layer, quickened: a pendulum clock in the next room (Freesound) | 68.00s | -28.2 dB | -8.0 dB | `90423841d7de4a80…` |
| `character_verdant.ogg` | character layer, verdant: an early-summer wood at dawn (Freesound) | 68.00s | -28.2 dB | -8.0 dB | `f9309a5a1bcdab33…` |
| `character_hollow.ogg` | character layer, hollow: water dripping in a cave (Freesound) | 68.00s | -28.1 dB | -8.0 dB | `c93dac3725e8e1b1…` |
| `character_overgrown.ogg` | character layer, overgrown: frogs and crickets at night (Freesound) | 68.00s | -28.0 dB | -8.0 dB | `9ca518d28ab90440…` |
