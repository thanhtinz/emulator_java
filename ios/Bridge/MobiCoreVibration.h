#import <Foundation/Foundation.h>

#import "core/com/mobicore/core/haptics/VibrationSink.h"

NS_ASSUME_NONNULL_BEGIN

/**
 * Carries the emulator's requests to vibrate to the phone's haptics.
 *
 * A J2ME game's only physical feedback was the handset shaking: the buzz on a
 * crash or a hit is part of what the game was. The core decides when and for
 * how long; this only carries it to the device.
 *
 * iOS has no "vibrate for 200 milliseconds" — it has haptic patterns and one
 * system buzz — so a longer request becomes a heavier one. That is as close as
 * the platform allows, and closer than nothing.
 */
@interface MobiCoreVibration : NSObject <ComMobicoreCoreHapticsVibrationSink>

@end

NS_ASSUME_NONNULL_END
