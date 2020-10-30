package com.tigertext.ttandroid.sample.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.collection.ArraySet
import com.tigertext.ttandroid.Group
import com.tigertext.ttandroid.Role
import com.tigertext.ttandroid.RosterEntry
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.entity.Entity2
import com.tigertext.ttandroid.sample.utils.PatientUtils.guessPatientContactId
import com.tigertext.ttandroid.sample.utils.TTUtilsForEntity.Companion.getUserToCallInRoleGroup
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity
import com.tigertext.ttandroid.sample.voip.VoIPManager
import com.tigertext.ttandroid.sample.voip.VoIPManager.convertCallInfoToExtras
import com.tigertext.ttandroid.sample.voip.VoIPManager.convertMappedSetToBundle
import com.tigertext.ttandroid.sample.voip.VoIPSelectionActivity
import timber.log.Timber
import java.util.*

/**
 * This is the new kotlin-based utils class that should eventually replace [TTUtilsForIntents]
 */
object TTIntentUtils {

    @JvmStatic
    fun getCallIntent(context: Context, entity: Entity2, callOptions: Set<Int>): Intent? {
        return when (callOptions.size) {
            0 -> null
            1 -> {
                val callOption = callOptions.iterator().next()
                when (callOption) {
                    VoIPSelectionActivity.VOIP_C2C_ID -> return getClickToCallIntent(context, entity)
                    VoIPSelectionActivity.VOIP_CALL_ID -> return getVoipCallIntent(context, entity, VoIPCallActivity.CALL_TYPE_AUDIO)
                    VoIPSelectionActivity.VOIP_VIDEO_ID -> return getVoipCallIntent(context, entity, VoIPCallActivity.CALL_TYPE_VIDEO)
                }
                Timber.e("Unknown Call Type: %s", callOption)
                null
            }
            else -> getCallSelectionIntent(context, entity, callOptions)
        }
    }

    private fun getVoipCallIntent(context: Context, entity: Entity2, callType: String): Intent? {
        return if (entity is Group && entity.isTypeRole) {
            val userToCall = getUserToCallInRoleGroup(entity) ?: return null
            getVoipCallIntent(callType, context, userToCall.displayName, setOf(userToCall.id), userToCall.orgId, null, entity.userToProxyMap)
        } else if (entity is Role) {
            val userToCall = entity.owners.firstOrNull() ?: return null
            val userToRoleIds = mapOf(Pair(userToCall.id, setOf(entity.id)))
            getVoipCallIntent(callType, context, userToCall.displayName, setOf(userToCall.id), userToCall.orgId, null, userToRoleIds)
        } else if (entity is User) {
            getVoipCallIntent(callType, context, entity.displayName, setOf(entity.id), entity.orgId, null, null)
        } else if (entity is Group && !entity.isRoom) {
            val recipients: MutableSet<String> = ArraySet(entity.memberIdsSet)
            recipients.remove(TT.getInstance().accountManager.userId)
            getVoipCallIntent(callType, context, entity.displayName, recipients, entity.orgId, entity.id, entity.userToProxyMap)
        } else {
            null
        }
    }

    private fun getVoipCallIntent(callType: String, context: Context, displayName: String?, recipientIds: Set<String>, orgId: String, groupId: String?, userToRoleIds: Map<String, Set<String>>?): Intent {
        val intent = Intent(context, VoIPCallActivity::class.java)
        intent.putExtra(VoIPCallActivity.EXTRA_DISPLAY_NAME, displayName)
        intent.putExtra(VoIPCallActivity.EXTRA_RECIPIENT_IDS, recipientIds.toTypedArray())
        intent.putExtra(VoIPCallActivity.EXTRA_ORGANIZATION_ID, orgId)
        intent.putExtra(VoIPCallActivity.EXTRA_GROUP_ID, groupId)
        intent.putExtra(VoIPCallActivity.EXTRA_IS_OUTGOING_CALL, true)
        intent.putExtra(VoIPCallActivity.EXTRA_CALL_TYPE, callType)
        if (userToRoleIds != null) {
            intent.putExtra(VoIPCallActivity.EXTRA_ROLE_IDS, convertMappedSetToBundle(userToRoleIds))
        }
        return intent
    }

    private fun getCallSelectionIntent(context: Context, entity: Entity2, callTypeOptions: Set<Int>?): Intent? {
        return if (entity is Group && entity.isTypeRole) {
            val userToCall = getUserToCallInRoleGroup(entity) ?: return null
            getCallSelectionIntent(context, userToCall.displayName, setOf(userToCall.id), userToCall.orgId, null, entity.userToProxyMap, callTypeOptions)
        } else if (entity is Role) {
            val owner = entity.owners.firstOrNull() ?: return null
            val userToRoleIds = mapOf(Pair(owner.id, setOf(entity.id)))
            getCallSelectionIntent(context, owner.displayName, setOf(owner.id), owner.orgId, null, userToRoleIds, callTypeOptions)
        } else if (entity is User) {
            getCallSelectionIntent(context, entity.displayName, setOf(entity.id), entity.orgId, null, null, callTypeOptions)
        } else if (entity is Group && !entity.isRoom) {
            val recipients: MutableSet<String> = ArraySet(entity.memberIdsSet)
            recipients.remove(TT.getInstance().accountManager.userId)
            getCallSelectionIntent(context, entity.displayName, recipients, entity.orgId, entity.id, entity.userToProxyMap, callTypeOptions)
        } else {
            null
        }
    }

    private fun getCallSelectionIntent(context: Context, displayName: String?, recipientIds: Set<String>, orgId: String, groupId: String?, userToRoleIds: Map<String, Set<String>>?, callTypeOptions: Set<Int>?): Intent {
        val intent = Intent(context, VoIPSelectionActivity::class.java)
        intent.putExtra(VoIPCallActivity.EXTRA_DISPLAY_NAME, displayName)
        intent.putExtra(VoIPCallActivity.EXTRA_RECIPIENT_IDS, recipientIds.toTypedArray())
        intent.putExtra(VoIPCallActivity.EXTRA_ORGANIZATION_ID, orgId)
        intent.putExtra(VoIPCallActivity.EXTRA_GROUP_ID, groupId)
        intent.putExtra(VoIPCallActivity.EXTRA_IS_OUTGOING_CALL, true)
        intent.putExtra(VoIPCallActivity.EXTRA_CALL_TYPE, VoIPCallActivity.CALL_TYPE_CHOOSE)
        if (userToRoleIds != null) {
            intent.putExtra(VoIPCallActivity.EXTRA_ROLE_IDS, convertMappedSetToBundle(userToRoleIds))
        }
        if (callTypeOptions != null) {
            intent.putExtra(VoIPSelectionActivity.EXTRA_CALL_TYPE_OPTIONS, ArrayList(callTypeOptions))
        }
        return intent
    }

    @JvmStatic
    fun getVoipCallIntent(context: Context?, callInfo: VoIPManager.CallInfo): Intent {
        val intent = Intent(context, VoIPCallActivity::class.java)
        val extras = Bundle()
        convertCallInfoToExtras(callInfo, extras)
        intent.putExtras(extras)
        intent.setDataAndNormalize(Uri.fromParts(callInfo.callType, callInfo.callId, null))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    fun getClickToCallIntent(context: Context?, callInfo: VoIPManager.CallInfo): Intent? {
        return getClickToCallIntent(context, callInfo.displayName, null, callInfo.recipientIds.iterator().next(), callInfo.orgId)
    }


    @JvmStatic
    fun getClickToCallIntent(context: Context, entity: Entity2): Intent? {
        if (entity is Group && entity.isTypeRole) {
            val userToCall = getUserToCallInRoleGroup(entity) ?: return null
            return getClickToCallIntent(context, userToCall)
        }
        if (entity is Role) {
            val owner = entity.owners.firstOrNull() ?: return null
            return getClickToCallIntent(context, owner)
        }
        if (entity is User) {
            return getClickToCallIntent(context, entity.displayName, entity.avatarUrl, entity.id, entity.orgId)
        }
        if (entity is Group) {
            if (RosterEntry.FEATURE_SERVICE_PATIENT_MESSAGING == entity.featureService) {
                val groupPatientInfo = entity.groupPatientInfo
                return if (groupPatientInfo!!.isPatientContact) {
                    val patientContactId = guessPatientContactId(entity)
                    if (!TextUtils.isEmpty(patientContactId)) {
                        getClickToCallIntent(context, entity.displayName, entity.avatarUrl, patientContactId, entity.orgId)
                    } else {
                        null
                    }
                } else {
                    getClickToCallIntent(context, entity.displayName, entity.avatarUrl, groupPatientInfo.patientId, entity.orgId)
                }
            }
        }
        return null
    }

    @JvmStatic
    fun getClickToCallIntent(context: Context?, displayName: String?, avatar: String?, userId: String?, orgId: String?): Intent? {
//        val intent = Intent(context, CallingStateActivity::class.java)
//        intent.putExtra(CallingStateActivity.USER_DISPLAY_NAME_EXTRA, displayName)
//        intent.putExtra(CallingStateActivity.USER_ID_EXTRA, userId)
//        intent.putExtra(CallingStateActivity.USER_AVATAR_EXTRA, avatar)
//        intent.putExtra(CallingStateActivity.USER_ORG_ID_EXTRA, orgId)
//        intent.putExtra(AnalyticsConstants.LAUNCH_ORIGIN, launchOrigin)
//        return intent
        return null
    }

}