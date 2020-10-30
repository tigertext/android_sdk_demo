package com.tigertext.voip.video

import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.group.Participant
import org.json.JSONObject

object DataTrackUtil {

    const val TYPE = "type"
    const val TYPE_REQUEST = "REQUEST"
    const val TYPE_RESPONSE = "RESPONSE"

    const val REQUEST_TYPE = "requestType"
    const val REQUEST_TYPE_GET_USER_INFO = "GET_USER_INFO"

    const val PAYLOAD = "payload"

    fun convertToUserInfo(participant: Participant): JSONObject {
        val userJson = JSONObject()
        val patientMetadata = (participant as? User)?.userPatientMetadata
        userJson.put("avatarUrl", participant.avatarUrl)
                .put("displayName", participant.displayName)
                .put("id", participant.id)
                .put("isPatient", patientMetadata != null && !patientMetadata.isPatientContact)
                .put("isPatientContact", patientMetadata?.isPatientContact == true)
        return userJson
    }

}