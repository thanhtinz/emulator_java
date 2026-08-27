#import "MobiCoreVibration.h"

#import <AudioToolbox/AudioToolbox.h>
#import <UIKit/UIKit.h>

@implementation MobiCoreVibration {
    UIImpactFeedbackGenerator *_light;
    UIImpactFeedbackGenerator *_heavy;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _light = [[UIImpactFeedbackGenerator alloc]
                initWithStyle:UIImpactFeedbackStyleLight];
        _heavy = [[UIImpactFeedbackGenerator alloc]
                initWithStyle:UIImpactFeedbackStyleHeavy];
        [_light prepare];
        [_heavy prepare];
    }
    return self;
}

- (jboolean)vibrateWithInt:(jint)durationMs {
    if (durationMs <= 0) {
        return true;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        if (durationMs >= 400) {
            // Long enough that the game means "shake the phone", not "tick".
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate);
        } else if (durationMs >= 120) {
            [self->_heavy impactOccurred];
        } else {
            [self->_light impactOccurred];
        }
    });
    return true;
}

- (void)cancel {
    // Nothing to stop: iOS haptics are instants, not a running motor.
}

@end
