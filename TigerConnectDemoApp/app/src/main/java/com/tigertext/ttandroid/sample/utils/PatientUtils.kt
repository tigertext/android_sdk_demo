package com.tigertext.ttandroid.sample.utils

import android.content.Context
import com.tigertext.ttandroid.Group
import com.tigertext.ttandroid.Message
import com.tigertext.ttandroid.RosterEntry
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.constant.TTConstants
import com.tigertext.ttandroid.message.PatientNetworkMessageMetadata
import com.tigertext.ttandroid.patient.GroupPatientInfo
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.search.SearchResultEntity
import timber.log.Timber

object PatientUtils {

    const val MEMBER_COUNT_GROUP = 3

    @JvmStatic
    fun getDisplayNameWithRelationString(context: Context, displayName: String?, relationName: String?, isPatientContact: Boolean): String {
        val relation = if (isPatientContact) relationName else context.getString(R.string.patient)
        return context.getString(R.string.join_string_parenthesis_string, displayName, relation)
    }

    @JvmStatic
    fun containsMultipleProviders(group: Group): Boolean {
        if (isPatientP2p(group)) return false

        val currentUserId = TT.getInstance().accountManager.userId
        val memberIdsSet = HashSet(group.memberIdsSet)

        memberIdsSet.remove(currentUserId)
        memberIdsSet.remove(group.groupPatientInfo?.patientId)
        group.userToProxyMap[currentUserId]?.let { memberIdsSet.removeAll(it) }

        for (memberId in memberIdsSet) {
            try {
                val participant = TT.getInstance().userManager.getParticipantLocally(memberId, group.orgId)
                if (participant?.featureService != RosterEntry.FEATURE_SERVICE_PATIENT_MESSAGING) return true
            } catch (e: Exception) {
                Timber.e(e, "Error retrieving participant")
            }
        }

        return false
    }

    @JvmStatic
    fun getDisplayNameFromPatientNetworkMetadata(context: Context, messageMetadata: PatientNetworkMessageMetadata?, message: Message): String? {
        if (messageMetadata?.isPatientContact == true && messageMetadata.contactId == message.originalSenderId) {
            return getDisplayNameWithRelationString(context, messageMetadata.contactName, messageMetadata.relationName, true)
        } else if (messageMetadata?.isPatientContact == false && messageMetadata.patientId == message.originalSenderId) {
            return getDisplayNameWithRelationString(context, messageMetadata.patientName, null, false)
        }
        return null
    }

    /**
     * TODO: This should eventually get replaced by [GroupPatientInfo.isPatientContact]
     */
    @JvmStatic
    fun isPatientContact(rosterEntry: RosterEntry): Boolean {
        return when (rosterEntry) {
            is User -> rosterEntry.userPatientMetadata?.isPatientContact == true
            is Group -> rosterEntry.groupPatientInfo?.isPatientContact == true
            else -> false
        }
    }

    /**
     * Since we aren't explicitly given the contact id in the group, we can only find it as a group member
     */
    @JvmStatic
    fun guessPatientContactId(group: Group): String? {
        if (group.memberCount >= MEMBER_COUNT_GROUP) return null
        val groupPatientInfo = group.groupPatientInfo ?: return null
        if (!groupPatientInfo.isPatientContact) return null
        return group.memberIdsSet.find { it != TT.getInstance().accountManager.userId }
    }

    @JvmStatic
    fun isPatientGroup(rosterEntry: RosterEntry): Boolean {
        return rosterEntry is Group && rosterEntry.featureService == RosterEntry.FEATURE_SERVICE_PATIENT_MESSAGING && rosterEntry.memberCount >= MEMBER_COUNT_GROUP
    }

    @JvmStatic
    fun isPatientP2p(rosterEntry: RosterEntry): Boolean {
        return rosterEntry is Group && rosterEntry.featureService == RosterEntry.FEATURE_SERVICE_PATIENT_MESSAGING && rosterEntry.memberCount < MEMBER_COUNT_GROUP
    }

    @JvmStatic
    fun doesDirectMessageExist(groups: List<Group>): Boolean {
        for (group in groups) {
            if (group.memberCount < MEMBER_COUNT_GROUP) {
                return true
            }
        }

        return false
    }

    /**
     * Search doesn't currently provide the patient info inside of the contact metadata.
     * Inject the patient data into the contact instead of passing the contact around as well.
     */
    @JvmStatic
    fun injectPatientDataIntoContactMetadata(patient: SearchResultEntity, contact: User) {
        val metadata = contact.ttMetadata?.optJSONObject(TTConstants.METADATA) ?: return
        val patientMetadata = patient.metadata ?: return

        metadata.put(PATIENT_ID, patient.id)
        metadata.put(PATIENT_NAME, patient.displayName)

        metadata.put("patient_mrn", patientMetadata["patient_mrn"])
        metadata.put("patient_dob", patientMetadata["patient_dob"])
        metadata.put("patient_gender", patientMetadata["patient_gender"])
    }

    private const val PATIENT_ID = "patient_id"
    private const val PATIENT_NAME = "patient_name"

}