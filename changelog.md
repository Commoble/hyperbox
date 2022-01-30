## 2.0.0.0
* Updated to minecraft 1.18.1+
* Hyperbox now requires the Infiniverse mod (1.0.0.1 or higher)
* Due to a small change in the way minecraft saves blockentity data, hyperbox blocks in worlds made in 1.16.x will lose their level and color data (the dimensions will still exist in your save folder but old hyperboxes will lose their link to them).
* The Server Config is now a Common Config (instead of a Server Config). This means that there is now one hyperbox config file in your minecraft instance's config folder (rather than a config file in each world save's config folder). Old configs will effectively be reset to their default values due to this change.
* New hyperboxes can no longer rarely generate with links to previously-generated hyperbox dimensions (unless many new hyperbox dimensions are generated in the same tick)
