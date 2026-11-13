# Parked: trueDarkness

`LightTextureMixin` is the Forge 1.20.1 / NeoForge 1.21.1 implementation of the `trueDarkness` option. It
rewrote the lightmap pixels on the CPU just before they were uploaded.

That hook does not exist on 26.3: the lightmap is drawn on the GPU by `net.minecraft.client.renderer.Lightmap`,
so there are no pixels to edit. The option is still in the config and the config screen, but it does nothing on
26.3 until this is rewritten (most likely as a change to the lightmap's inputs rather than its output).

The file here is the 1.21.1 version, unedited. It is not compiled.
