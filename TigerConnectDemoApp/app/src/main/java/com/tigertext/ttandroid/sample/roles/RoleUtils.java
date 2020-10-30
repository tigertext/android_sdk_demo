package com.tigertext.ttandroid.sample.roles;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.group.Participant;
import com.tigertext.ttandroid.roles.RoleRosterProperties;

import java.util.Set;

/**
 * This class holds all the methods and cache for roles
 */
public class RoleUtils {

    public static boolean isMyRole(String roleId, String orgId) {
        if (TextUtils.isEmpty(roleId) || TextUtils.isEmpty(orgId)) return false;
        return TT.getInstance().getRoleManager().getOptedIntoRoleIdsSet(orgId).contains(roleId);
    }

    public static boolean isMyParticipant(String participantId, String orgId) {
        if (TextUtils.isEmpty(participantId)) return false;
        return participantId.equals(TT.getInstance().getAccountManager().getUserId()) || isMyRole(participantId, orgId);
    }

    /**
     *
     * @param rosterEntry
     * @return The proper display name for the roster entry, considering if the role is a fake p2p and what roles we are on duty for.
     */
    public static String getRosterDisplayNameConsideringRolesOnDuty(RosterEntry rosterEntry) {
        String displayName = rosterEntry.getDisplayName();
        if (rosterEntry.isTypeRole()) {
            Set<String> orgOptedRoles = TT.getInstance().getRoleManager().getOptedIntoRoleIdsSet(rosterEntry.getOrgId());
            RoleRosterProperties props = rosterEntry.getRoleRosterProps();
            if (props.isCreatorRole()) { // is creator a role
                /* Case 2 : Role created conversation with a Person */
                if (orgOptedRoles.contains(props.getRoleCreatedbyId())) {
                    displayName = props.getRoleTargetName();
                } else {
                    displayName = props.getRoleCreatorName();
                }
            } else if (props.isTargetRole()) { // is target a role
                /* Case 3 : Person created conversation with a Role */
                if (orgOptedRoles.contains(props.getRoleTargetId())) {
                    displayName = props.getRoleCreatorName();
                } else {
                    displayName = props.getRoleTargetName();
                }
            }
        }
        return displayName;
    }

    /**
     * Gets the participant locally (cache or DB) associated with a Fake P2P conversation,
     * with the option to get "Mine" or "Other"
     * @param roleRoster Fake P2P Conversation, isTypeRole
     * @param shouldReturnMyParticipant true for "Mine", false for "Other"
     * @return My Participant or Other Participant, can be null
     * @see RolePropertiesHelper
     */
    public static @Nullable
    Participant getParticipantFromRoleRoster(RosterEntry roleRoster, boolean shouldReturnMyParticipant) {
        if (roleRoster == null || !roleRoster.isTypeRole()) return null;

        RoleRosterProperties props = roleRoster.getRoleRosterProps();

        final boolean isCreatorMine = RolePropertiesHelper.isCreatorMine(props, roleRoster.getOrgId());
        if (shouldReturnMyParticipant) {
            return TT.getInstance().getUserManager().getParticipantLocally(RolePropertiesHelper.getMyId(props, isCreatorMine), roleRoster.getOrgId());
        } else {
            return TT.getInstance().getUserManager().getParticipantLocally(RolePropertiesHelper.getOtherId(props, isCreatorMine), roleRoster.getOrgId());
        }
    }

}
