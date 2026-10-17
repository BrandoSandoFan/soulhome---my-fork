# Where these sounds came from

Every file in this directory is a mix of recordings of real instruments, made by
`tools/ambience/mix.py` (#163). The recordings come from two libraries, both released
under **CC0 1.0** (public domain dedication), so nothing here carries an attribution
requirement - the credit below is given because it is owed, not because it is required:

* **VSCO-2 Community Edition**, Versilian Studios -
  <https://github.com/sgossner/VSCO-2-CE>, commit `440300901dfe9275fd84e0b7763af1f8443ae62e`
* **Versilian Community Sample Library (VCSL)**, Versilian Studios and contributors -
  <https://github.com/sgossner/VCSL>, commit `c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e`

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
| `base_1.ogg` | one-shot, base, variant 1: bowed vibraphone over a barely-touched gong (VCSL, VSCO-2) | 4.50s | -20.0 dB | -9.7 dB | `b9abeff3e8cae676…` |
| `base_2.ogg` | one-shot, base, variant 2: bowed vibraphone over a barely-touched gong (VCSL, VSCO-2) | 4.50s | -20.0 dB | -9.0 dB | `ca77c056dad1ff76…` |
| `base_3.ogg` | one-shot, base, variant 3: bowed vibraphone over a barely-touched gong (VCSL, VSCO-2) | 4.50s | -20.0 dB | -8.0 dB | `3c3414ff09a1e18e…` |
| `warm_1.ogg` | one-shot, warm, variant 1: low F horn, a hand on a frame drum, one clave pop (VSCO-2, VCSL) | 3.00s | -20.0 dB | -7.2 dB | `34ae5abeced792e6…` |
| `warm_2.ogg` | one-shot, warm, variant 2: low F horn, a hand on a frame drum, one clave pop (VSCO-2, VCSL) | 3.00s | -20.0 dB | -6.0 dB | `b03e64b72948f943…` |
| `warm_3.ogg` | one-shot, warm, variant 3: low F horn, a hand on a frame drum, one clave pop (VSCO-2, VCSL) | 3.00s | -20.0 dB | -3.0 dB | `db96ef2e0f12c079…` |
| `cold_1.ogg` | one-shot, cold, variant 1: glockenspiel strike into a rubbed wine glass (VSCO-2, VCSL) | 4.00s | -20.0 dB | -6.7 dB | `bb090f3c4b6abe1c…` |
| `cold_2.ogg` | one-shot, cold, variant 2: glockenspiel strike into a rubbed wine glass (VSCO-2, VCSL) | 4.00s | -20.0 dB | -7.0 dB | `84a8a326a58be4a3…` |
| `cold_3.ogg` | one-shot, cold, variant 3: glockenspiel strike into a rubbed wine glass (VSCO-2, VCSL) | 4.00s | -20.0 dB | -8.9 dB | `d5636aed8b2e206a…` |
| `steam_1.ogg` | one-shot, steam, variant 1: wine-glass ring cut short by cabasa breaths (VCSL) | 3.40s | -20.0 dB | -5.4 dB | `b6c7d9caba839f90…` |
| `steam_2.ogg` | one-shot, steam, variant 2: wine-glass ring cut short by cabasa breaths (VCSL) | 3.40s | -20.0 dB | -4.1 dB | `28b17aea5cb0f39f…` |
| `steam_3.ogg` | one-shot, steam, variant 3: wine-glass ring cut short by cabasa breaths (VCSL) | 3.40s | -20.0 dB | -5.6 dB | `243f690357ec1d77…` |
| `arcane_1.ogg` | one-shot, arcane, variant 1: reversed hand chime into a lydian harp figure and bell tree (VCSL, VSCO-2) | 4.20s | -20.0 dB | -5.9 dB | `17814c5462768b7f…` |
| `arcane_2.ogg` | one-shot, arcane, variant 2: reversed hand chime into a lydian harp figure and bell tree (VCSL, VSCO-2) | 4.20s | -20.0 dB | -4.1 dB | `d700a81187963ecd…` |
| `arcane_3.ogg` | one-shot, arcane, variant 3: reversed hand chime into a lydian harp figure and bell tree (VCSL, VSCO-2) | 4.20s | -20.0 dB | -8.2 dB | `975a6190876bb41b…` |
| `wrought_1.ogg` | one-shot, wrought, variant 1: anvil over a lowpassed timpani (VCSL) | 3.20s | -20.1 dB | -3.0 dB | `52136508670369fa…` |
| `wrought_2.ogg` | one-shot, wrought, variant 2: anvil over a lowpassed timpani (VCSL) | 3.20s | -20.6 dB | -3.0 dB | `b0a53ac732d07009…` |
| `wrought_3.ogg` | one-shot, wrought, variant 3: anvil over a lowpassed timpani (VCSL) | 3.20s | -22.3 dB | -3.0 dB | `6f39ced35bf8c064…` |
| `quickened_1.ogg` | one-shot, quickened, variant 1: accelerating woodblock into glockenspiel and anvil (VCSL, VSCO-2) | 4.00s | -20.4 dB | -3.0 dB | `de5def7bbf015286…` |
| `quickened_2.ogg` | one-shot, quickened, variant 2: accelerating woodblock into glockenspiel and anvil (VCSL, VSCO-2) | 4.00s | -20.0 dB | -3.0 dB | `a77d3edf02fcb2c0…` |
| `quickened_3.ogg` | one-shot, quickened, variant 3: accelerating woodblock into glockenspiel and anvil (VCSL, VSCO-2) | 4.00s | -20.3 dB | -3.0 dB | `d64efe94d0941fa2…` |
| `verdant_1.ogg` | one-shot, verdant, variant 1: flute staccato call over soft marimba and shaker (VSCO-2, VCSL) | 3.40s | -20.0 dB | -3.0 dB | `a786cff0dd8e4ee9…` |
| `verdant_2.ogg` | one-shot, verdant, variant 2: flute staccato call over soft marimba and shaker (VSCO-2, VCSL) | 3.40s | -20.0 dB | -3.3 dB | `0dd7a2979d0db206…` |
| `verdant_3.ogg` | one-shot, verdant, variant 3: flute staccato call over soft marimba and shaker (VSCO-2, VCSL) | 3.40s | -20.1 dB | -3.0 dB | `1986494c35a0057a…` |
| `hollow_1.ogg` | one-shot, hollow, variant 1: bass drum heartbeat and a tubular bell tuned down, far off (VCSL) | 4.20s | -20.0 dB | -4.0 dB | `75c9a4f28f687e0c…` |
| `hollow_2.ogg` | one-shot, hollow, variant 2: bass drum heartbeat and a tubular bell tuned down, far off (VCSL) | 4.20s | -20.0 dB | -8.3 dB | `5af0e85b605af311…` |
| `hollow_3.ogg` | one-shot, hollow, variant 3: bass drum heartbeat and a tubular bell tuned down, far off (VCSL) | 4.20s | -20.0 dB | -7.0 dB | `d423862952384bc1…` |
| `overgrown_1.ogg` | one-shot, overgrown, variant 1: low soft marimba, shaker leaves, a damped glockenspiel drop (VCSL, VSCO-2) | 4.00s | -20.0 dB | -8.7 dB | `7486faf6d9c1889c…` |
| `overgrown_2.ogg` | one-shot, overgrown, variant 2: low soft marimba, shaker leaves, a damped glockenspiel drop (VCSL, VSCO-2) | 4.00s | -20.0 dB | -8.8 dB | `65405bfe14fea4d0…` |
| `overgrown_3.ogg` | one-shot, overgrown, variant 3: low soft marimba, shaker leaves, a damped glockenspiel drop (VCSL, VSCO-2) | 4.00s | -20.0 dB | -8.9 dB | `87169e4110d74501…` |
| `bed_close.ogg` | rank bed, close: dark ocean drum and a rubbed bass drum skin, seamless loop | 68.00s | -24.0 dB | -8.5 dB | `4274413b792ae795…` |
| `bed_open.ogg` | rank bed, open: lifted ocean drum, distant cymbal roll and gong roll, very wet, seamless loop | 68.00s | -24.0 dB | -8.0 dB | `22900017362ea0c6…` |
| `character_warm.ogg` | character layer, warm: fire built from claves, woodblock, cabasa and shaker transients over a low ocean drum | 68.00s | -28.0 dB | -8.0 dB | `06480914c28a7ee7…` |
| `character_cold.ogg` | character layer, cold: high-passed ocean drum for thin wind, sparse distant triangle | 68.00s | -28.0 dB | -9.4 dB | `825623b3f836b37e…` |
| `character_steam.ogg` | character layer, steam: shaker hiss in gusts, sparse wine-glass rings, a few embers | 68.00s | -28.0 dB | -8.0 dB | `2dd10409c2d4859b…` |
| `character_arcane.ogg` | character layer, arcane: bowed cymbal shimmer, sparse bell tree strokes, a very large room | 68.00s | -28.0 dB | -9.5 dB | `d436e4bae87d8c64…` |
| `character_wrought.ogg` | character layer, wrought: pp bass drum roll for the forge floor, distant lowpassed anvil | 68.00s | -28.0 dB | -13.7 dB | `4e58a2402a3907af…` |
| `character_quickened.ogg` | character layer, quickened: bursts of woodblock ticking, a muted triangle now and then | 68.00s | -28.3 dB | -8.0 dB | `f4bcea5c237cd4cd…` |
| `character_verdant.ogg` | character layer, verdant: shaker leaves and far-off flute-staccato birds | 68.00s | -28.0 dB | -8.0 dB | `47a2bc75b731a7d7…` |
| `character_hollow.ogg` | character layer, hollow: low ocean drum, slow bass drum heartbeats, a gong tuned down to a pressure change | 68.00s | -28.0 dB | -11.1 dB | `0324a001b0fe8c36…` |
| `character_overgrown.ogg` | character layer, overgrown: dark shaker leaves, damp ocean drum, damped glockenspiel drips | 68.00s | -28.0 dB | -9.1 dB | `46b1530adf6ad657…` |
