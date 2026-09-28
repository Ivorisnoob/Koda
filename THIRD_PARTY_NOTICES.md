# Third-party notices

Koda is licensed under the [GNU General Public License v3.0](LICENSE). The open-source libraries it is built on, and their licences, are listed in the app under **Open-source licences** in Settings' about section, generated from the build's dependency graph.

This file credits work that shaped Koda's own code without being a dependency of it.

## Smooth motion (video frame interpolation)

Koda's Smooth motion engines (`app/src/main/java/com/ivor/ivormusic/service/ComputeMotionEngine.kt` and `FragmentMotionEngine.kt`) were designed after studying the following open-source projects. No source code was copied: Koda's shaders and Kotlin were written from scratch for Media3's video pipeline and OpenGL ES. The methods below are what we learned from them, and the credit is theirs.

### AMD FidelityFX SDK - FSR 3 frame interpolation and optical flow

<https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK>, studied at commit `60f4ea8` (June 2026).

What Koda's engines take from it:

- hierarchical 8x8 block matching with an exhaustive search at every pyramid level;
- choosing among the four nearest coarse vectors when moving to a finer level;
- the 3x3 vector median that keeps a vector a neighbour really had;
- pushing each vector to its position at the interpolated moment with an atomic max on a packed priority, so the content that should be in front wins a collision.

Licensed under the MIT License:

```
Copyright (C) 2026 Advanced Micro Devices, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files(the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and /or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions :

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

### FFmpeg - the `minterpolate` filter

<https://github.com/FFmpeg/FFmpeg/blob/master/libavfilter/vf_minterpolate.c>, copyright (c) 2014-2015 Michael Niedermayer and (c) 2016 Davinder Singh, part of FFmpeg, licensed under the GNU Lesser General Public License 2.1 or later.

What Koda's engines take from it:

- bilateral (symmetric) motion estimation at the midpoint, the basis of the portable engine;
- accepting a finer vector only when it clearly beats the coarser one (a lesson learned while testing a thin-object pass that was later dropped);
- weighting overlapping motion by how well each candidate fits, which Koda does per pixel.
