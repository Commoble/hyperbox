## 5.0.0.0
* Updated to neoforge / MC-1.20.4
* Hyperbox interiors now generate a special block for the walls instead of bedrock (properties of this block are identical to bedrock)

## 4.0.1.0
* Fixed Mood sounds not working inside hyperboxes (requires both server and client to update hyperbox)

## 4.0.0.1
* Fixed hyperbox dimensions sometimes not saving when breaking the box
* Fixed withers being able to destroy apertures inside hyperboxes

## 4.0.0.0
* Updated to 1.20.1. Requires forge 47.0.3 or higher. Old worlds are not compatible.
* Infiniverse 1.0.0.5 is now bundled with Hyperbox via forge's Jar-In-Jar system (you don't have to install Infiniverse yourself anymore unless you need to use a newer version of infiniverse)
* Hyperbox rooms now generate at chunk 0,0 in each hyperbox dimension instead of chunk 16,16

## 3.0.1.0
* Now requires 1.19.2 forge build 43.1.0 or higher
* The name-your-hyperbox menu now opens with the dimension id field focused
* Pressing the 'Enter' key on the name-your-hyperbox screen now saves the hyperbox id and enters the hyperbox
* The 'hyperbox' nbt file in each hyperbox dimension's data folder is now called 'hyperbox.dat'. Due to the below bug, this file wasn't actually being read properly, so this *probably* won't affect anyone.
* Fixed a bug where players logging out while in hyperboxes would log back in at their spawn point instead of the hyperbox (you'll need to re-enter your hyperbox to apply this fix)
* Fixed a bug where pressing the inventory key while in the name-your-hyperbox menu would close the menu

## 3.0.0.0
* Updated to 1.19 forge build 41.0.110
* Hyperboxes now prompt the user for a dimension id and name when first used (no more random UUID dimensions)

## 2.0.0.2
* Fix NPE crash from attempting to get sideless capabilities from aperture and hyperbox blockentities
* Now requires forge 40.0.35 or higher

## 2.0.0.1
* Updated to minecraft 1.18.2+ (now requires infiniverse 1.18.2-1.0.0.2+)

## 2.0.0.0
* Updated to minecraft 1.18.1+
* Hyperbox now requires the Infiniverse mod (1.0.0.1 or higher)
* Due to a small change in the way minecraft saves blockentity data, hyperbox blocks in worlds made in 1.16.x will lose their level and color data (the dimensions will still exist in your save folder but old hyperboxes will lose their link to them).
* The Server Config is now a Common Config (instead of a Server Config). This means that there is now one hyperbox config file in your minecraft instance's config folder (rather than a config file in each world save's config folder). Old configs will effectively be reset to their default values due to this change.
* New hyperboxes can no longer rarely generate with links to previously-generated hyperbox dimensions (unless many new hyperbox dimensions are generated in the same tick)
