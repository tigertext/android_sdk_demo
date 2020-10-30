package com.tigertext.ttandroid.sample.roles;

import androidx.annotation.Nullable;

import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.roles.RoleRosterProperties;
import com.tigertext.ttandroid.roles.RoleTag;

/**
 * Created by carydobeck on 2/1/17.
 *
 * Helper class for RoleRosterProperties to facilitate getting role info
 * based on whether a Role is "Mine" or "Not Mine/Other" instead of the arbitrary "Creator" and "Target"
 * "Mine" = Who I am
 * "Other" = Who I am talking to
 * @see RosterEntry#isTypeRole() for Fake P2P conversations that contain RoleRosterProperties
 * @see RosterEntry#getRoleRosterProps()
 * @see RoleRosterProperties
 * @see RoleUtils#isMyParticipant(String, String)
 */

public class RolePropertiesHelper {

    /**
     * Helper method for determining whether creator is Mine,
     * useful for using the rest of the methods in this class.
     * @param props The RoleRosterProperties for the conversation
     * @return true if Creator is my Role, false if not
     * @see RoleUtils#isMyParticipant(String, String)
     */
    public static boolean isCreatorMine(RoleRosterProperties props, String orgId) {
        return RoleUtils.isMyParticipant(props.getRoleCreatedbyId(), orgId);
    }

    public static boolean isMineTypeRole(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.isCreatorRole() : props.isTargetRole();
    }

    public static boolean isOtherTypeRole(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.isTargetRole() : props.isCreatorRole();
    }

    public static String getMyId(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.getRoleCreatedbyId() : props.getRoleTargetId();
    }

    public static String getOtherId(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.getRoleTargetId() : props.getRoleCreatedbyId();
    }

    public static String getMyDisplayName(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.getRoleCreatorName() : props.getRoleTargetName();
    }

    public static String getOtherDisplayName(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.getRoleTargetName() : props.getRoleCreatorName();
    }

    @Nullable
    public static RoleTag getMyTag(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.getRoleCreatorTag() : props.getRoleTargetTag();
    }

    @Nullable
    public static RoleTag getOtherTag(RoleRosterProperties props, boolean isCreatorMine) {
        return isCreatorMine ? props.getRoleTargetTag() : props.getRoleCreatorTag();
    }

}
