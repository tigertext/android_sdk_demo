package com.tigertext.ttandroid.sample.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import com.tigertext.ttandroid.Group
import com.tigertext.ttandroid.Role
import com.tigertext.ttandroid.RosterEntry
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.entity.Entity2
import com.tigertext.ttandroid.org.Organization
import com.tigertext.ttandroid.sample.utils.ui.ButtonState
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter
import com.tigertext.ttandroid.settings.SettingType
import com.tigertext.ttandroid.sample.voip.VoIPSelectionActivity
import timber.log.Timber

object TTCallUtils {

    const val PROXY_CONTACTS_MESSAGE_DISPLAY_MAX_COUNT = 1

    @JvmStatic
    fun deviceHasCallingCapabilities(context: Context): Boolean {
        val packageManager = context.packageManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        //Validate that the device has all the capabilities we need...
        val hasTelephonyCapabilities = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA)

        if (!hasTelephonyCapabilities) return false

        // Check if device can make calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (!telephonyManager.isVoiceCapable) {
                Timber.v("The Device do not have Calling Capabilities")
                return false
            }
        }

        //Make sure that there's a sim card plugged...
        val simState = telephonyManager.simState
        return simState != TelephonyManager.SIM_STATE_ABSENT
    }

    @JvmStatic
    fun isCapableOfClickToCall(organization: Organization?, context: Context): Boolean {
        return (organization != null && organization.isClickToCallEnabled && deviceHasCallingCapabilities(context)
                && organization.settings[SettingType.CLICK_TO_CALL]?.value == true)
    }

    @JvmStatic
    fun isCapableOfQuickCall(organization: Organization?, context: Context): Boolean {
        return (organization != null && deviceHasCallingCapabilities(context)
                && organization.settings[SettingType.PF_QUICK_CALL]?.value == true)
    }

    @JvmStatic
    fun isCapableOfP2pVoipAudio(organization: Organization?): Boolean {
        return organization != null && organization.isVoipEnabled && organization.settings[SettingType.VOIP]?.value == true
    }

    @JvmStatic
    fun isCapableOfP2pVoipVideo(organization: Organization?): Boolean {
        return organization != null && organization.isVideoEnabled && organization.settings[SettingType.VIDEO_CALL]?.value == true
    }

    @JvmStatic
    fun isCapableOfP2pVoip(organization: Organization?): Boolean {
        return isCapableOfP2pVoipAudio(organization) || isCapableOfP2pVoipVideo(organization)
    }

    @JvmStatic
    fun isCapableOfGroupVoipAudio(organization: Organization?): Boolean {
        return organization != null && organization.isGroupAudioEnabled && organization.settings[SettingType.GROUP_AUDIO_CALL]?.value == true
    }

    @JvmStatic
    fun isCapableOfGroupVoipVideo(organization: Organization?): Boolean {
        return organization != null && organization.isGroupVideoEnabled && organization.settings[SettingType.GROUP_VIDEO_CALL]?.value == true
    }

    @JvmStatic
    fun isCapableOfGroupVoip(organization: Organization?) = isCapableOfGroupVoipAudio(organization) || isCapableOfGroupVoipVideo(organization)

    @JvmStatic
    fun isCapableOfVoip(organization: Organization?) = isCapableOfP2pVoip(organization) || isCapableOfGroupVoip(organization)

    @JvmStatic
    fun isCapableOfPatientVoip(organization: Organization) = organization.settings[SettingType.PF_VOIP]?.value == true
            || organization.settings[SettingType.PF_VIDEO_CALL]?.value == true
            || organization.settings[SettingType.PF_GROUP_AUDIO_CALL]?.value == true
            || organization.settings[SettingType.PF_GROUP_VIDEO_CALL]?.value == true

    @JvmStatic
    fun hasAtLeastOneOrgCapableOfClickToCall(organizations: Collection<Organization>?, context: Context) = organizations?.firstOrNull { isCapableOfClickToCall(it, context) } != null

    @JvmStatic
    fun hasAtLeastOneOrgCapableOfVoip(organizations: Collection<Organization>?) = organizations?.firstOrNull { isCapableOfVoip(it) } != null

    @JvmStatic
    fun getCallButtonState(entity: Entity2?, context: Context): ButtonState {
        entity ?: return ButtonState.GONE
        val org = TT.getInstance().organizationManager.getOrganization(entity.orgId)
                ?: return ButtonState.GONE

        if (entity is Role) {
            return getCallButtonState(entity.owners.firstOrNull(), context)
        }
        if (entity !is RosterEntry) return ButtonState.GONE

        if (entity.featureService == RosterEntry.FEATURE_SERVICE_PATIENT_MESSAGING) {
            if ((entity is Group && entity.groupPatientInfo?.smsOptedOut == true)
                    || (entity is User && entity.userPatientMetadata?.smsOptedOut == true)) {
                return ButtonState.GONE
            }

            if (entity is User || PatientUtils.isPatientP2p(entity)) {
                return if (isCapableOfClickToCall(org, context)
                        || org.settings[SettingType.PF_VOIP]?.value == true
                        || org.settings[SettingType.PF_VIDEO_CALL]?.value == true) {
                    ButtonState.CLICKABLE
                } else ButtonState.GONE
            } else if (entity is Group && PatientUtils.isPatientGroup(entity)) {
                return if (org.settings[SettingType.PF_GROUP_AUDIO_CALL]?.value == true
                        || org.settings[SettingType.PF_GROUP_VIDEO_CALL]?.value == true) {
                    if (TTUtils.getUniqueMembersCount(entity, null) > TwilioVideoPresenter.PARTICIPANTS_PATIENTS_MAX)
                        ButtonState.DISABLED else ButtonState.CLICKABLE
                } else ButtonState.GONE
            } else {
                ButtonState.GONE
            }
        }

        if (entity is User) {
            if (TTUtilsForEntity.isTigerPage(entity)) return ButtonState.GONE
            return if ((entity.canReceiveCalls() && isCapableOfClickToCall(org, context)) || isCapableOfP2pVoip(org)) {
                if (entity.isDnd) return ButtonState.DISABLED
                if (entity.id == TT.getInstance().accountManager.userId) return ButtonState.DISABLED
                ButtonState.CLICKABLE
            } else {
                ButtonState.GONE
            }
        }

        if (entity.isTypeRole) {
            if (TTUtilsForEntity.isTigerPage(entity)) return ButtonState.GONE
            return if (isCapableOfClickToCall(org, context) || isCapableOfP2pVoip(org)) {
                return if (!TTUtilsForEntity.getUserIdToCallInRoleGroup(entity as Group).isNullOrEmpty()) ButtonState.CLICKABLE else ButtonState.DISABLED
            } else ButtonState.GONE
        }
        if (entity is Group) {
            return when {
                !isCapableOfGroupVoip(org) -> ButtonState.GONE
                entity.isPublic -> ButtonState.GONE
                TTUtils.getUniqueMembersCount(entity, null) > TwilioVideoPresenter.PARTICIPANTS_MAX -> ButtonState.DISABLED
                else -> ButtonState.CLICKABLE
            }
        }

        return ButtonState.GONE
    }

    /**
     * Calculates the call options that are available for calling the given roster entry
     */
    @JvmStatic
    fun getCallOptions(entity: Entity2?, context: Context): Set<Int> {
        entity ?: return setOf()
        val org = TT.getInstance().organizationManager.getOrganization(entity.orgId)
                ?: return setOf()

        val buttonState = getCallButtonState(entity, context)
        if (buttonState != ButtonState.CLICKABLE) return setOf()

        val options = mutableSetOf<Int>()

        if (entity is RosterEntry && entity.featureService == RosterEntry.FEATURE_SERVICE_PATIENT_MESSAGING) {
            if (entity is User || PatientUtils.isPatientP2p(entity)) {
                if (isCapableOfClickToCall(org, context)) {
                    options.add(VoIPSelectionActivity.VOIP_C2C_ID)
                }
                if (org.settings[SettingType.PF_VOIP]?.value == true) {
                    options.add(VoIPSelectionActivity.VOIP_CALL_ID)
                }
                if (org.settings[SettingType.PF_VIDEO_CALL]?.value == true) {
                    options.add(VoIPSelectionActivity.VOIP_VIDEO_ID)
                }
            } else if (PatientUtils.isPatientGroup(entity)) {
                if (org.settings[SettingType.PF_GROUP_AUDIO_CALL]?.value == true) {
                    options.add(VoIPSelectionActivity.VOIP_CALL_ID)
                }
                if (org.settings[SettingType.PF_GROUP_VIDEO_CALL]?.value == true) {
                    options.add(VoIPSelectionActivity.VOIP_VIDEO_ID)
                }
            }
        } else if (entity is User || entity is Role || (entity is Group && entity.isTypeRole)) {
            if (isCapableOfClickToCall(org, context)) {
                options.add(VoIPSelectionActivity.VOIP_C2C_ID)
            }
            if (isCapableOfP2pVoipAudio(org)) {
                options.add(VoIPSelectionActivity.VOIP_CALL_ID)
            }
            if (isCapableOfP2pVoipVideo(org)) {
                options.add(VoIPSelectionActivity.VOIP_VIDEO_ID)
            }
        } else if (entity is Group) {
            if (isCapableOfGroupVoipAudio(org)) {
                options.add(VoIPSelectionActivity.VOIP_CALL_ID)
            }
            if (isCapableOfGroupVoipVideo(org)) {
                options.add(VoIPSelectionActivity.VOIP_VIDEO_ID)
            }
        }

        return options
    }

}