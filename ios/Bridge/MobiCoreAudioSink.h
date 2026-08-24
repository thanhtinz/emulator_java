#import <Foundation/Foundation.h>

#import "core/com/mobicore/core/audio/AudioSink.h"

NS_ASSUME_NONNULL_BEGIN

/**
 * Plays what the emulator produces through the system audio output.
 *
 * The core hands over a finished block of samples, which is what J2ME audio
 * actually is — a beep, a short effect, a looping tune — so each sound becomes
 * one `AVAudioPlayer`. Nothing here mixes: the system already does that, and
 * far better than anything written here would.
 *
 * Conforms to the translated `AudioSink` protocol, so the emulator core needs
 * to know nothing about the platform it ended up on.
 */
@interface MobiCoreAudioSink : NSObject <ComMobicoreCoreAudioAudioSink>

/// Stops every voice and releases them. Call when a game stops.
- (void)releaseAll;

@end

NS_ASSUME_NONNULL_END
