package com.tigertext.ttandroid.sample.utils

import android.text.TextUtils
import androidx.collection.ArraySet
import com.tigertext.ttandroid.*
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.constant.TTConstants
import com.tigertext.ttandroid.group.Participant
import com.tigertext.ttandroid.sample.roles.RolePropertiesHelper
import com.tigertext.ttandroid.search.ReturnField2
import com.tigertext.ttandroid.search.SearchResult
import com.tigertext.ttandroid.search.SearchResultEntity
import com.tigertext.ttandroid.team.Team
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

/**
 * Created by martincazares on 10/12/17.
 */
class TTUtilsForEntity {

    companion object {
        // TigerPage
        private val TIGER_PAGE_IDS = setOf("999760f7-110b-4c9a-8d26-7520918896a5", "ccd0b6da-e5c8-40fe-9708-828fa8fef035", "f3e06a6f-e509-4f65-9d8f-bd6fbafcba02", "e5ef47bd-6ab9-4ece-9b3e-aa477056d086")

        @JvmStatic
        fun isTigerPage(rosterEntry: RosterEntry): Boolean {
            val id1: String
            var id2: String? = null
            if (rosterEntry.isTypeRole) {
                val properties = rosterEntry.roleRosterProps
                id1 = properties.roleCreatedbyId
                id2 = properties.roleTargetId
            } else {
                id1 = rosterEntry.id
            }

            return TIGER_PAGE_IDS.contains(id1) || TIGER_PAGE_IDS.contains(id2)
        }

        @JvmStatic
        fun getUserIdToCallInRoleGroup(group: Group): String? {
            val props = group.roleRosterProps
            val isCreatorMine = RolePropertiesHelper.isCreatorMine(props, group.orgId)
            val otherId = RolePropertiesHelper.getOtherId(props, isCreatorMine)
            return if (RolePropertiesHelper.isOtherTypeRole(props, isCreatorMine)) {
                group.proxyToUserMap[otherId]
            } else {
                otherId
            }
        }

        /**
         * Get the owner of the role or null if I'm the owner or there's
         * no owner for this role...
         */
        @JvmStatic
        fun getUserToCallInRoleGroup(group: Group): User? {
            val userId = getUserIdToCallInRoleGroup(group)
            if (userId.isNullOrEmpty()) return null
            return TT.getInstance().userManager.getUserSync(userId, group.orgId)
        }

        @JvmStatic
        fun getOwnerForRole(role: Role?): User? = role?.owners?.firstOrNull()

        @JvmStatic
        fun getParticipantFromSearchEntity(searchResult: SearchResult): Participant {
            if (searchResult.type == Entity.TYPE_ACCOUNT) {
                return if (searchResult.entity.isRole) {
                    Role(searchResult.entity.token,
                            searchResult.entity.displayName,
                            searchResult.organizationId,
                            searchResult.entity.roleTag,
                            searchResult.entity.escalationPolicy)
                } else {
                    getUserFromSearchEntity(searchResult.entity)
                }
            }
            if (searchResult.type == Entity.TYPE_TEAM) {
                return Team(id = searchResult.entity.token,
                        orgId = searchResult.organizationId,
                        displayName = searchResult.entity.displayName,
                        avatarUrl = searchResult.entity.avatarUrl,
                        description = searchResult.entity.description,
                        groupId = null,
                        canRequestToJoin = searchResult.entity.canRequestToJoin(),
                        memberIds = searchResult.entity.memberSet,
                        pendingUserIds = setOf(),
                        canMembersLeave = searchResult.entity.canMembersLeave())
            }

            throw IllegalArgumentException("Unknown type: $searchResult")
        }

        @JvmStatic
        fun getUserFromSearchEntity(searchResultEntity: SearchResultEntity): User {
            val userEntry = User.getUserEntry(searchResultEntity.token)
            with(userEntry) {
                setRosterDisplayName(searchResultEntity.displayName)
                setRosterType(Entity.TYPE_ACCOUNT)
                setRosterOrgId(searchResultEntity.orgKey);setRosterAvatarUrl(searchResultEntity.avatarUrl)
                setRosterTitle(searchResultEntity.getReturnField(ReturnField2.TITLE));setRosterDepartment(searchResultEntity.getReturnField(ReturnField2.DEPARTMENT))
                setRosterUserName(searchResultEntity.username); dndAutoForwardReceiverId = searchResultEntity.dndAutoForwardReceiver
                isDnd = searchResultEntity.dnd
                searchResultEntity.metadata?.let { ttMetadata = JSONObject().put(TTConstants.METADATA, it) }
            }

            return userEntry
        }

        @JvmStatic
        fun getGroupFromSearchEntity(searchResult: SearchResultEntity): Group {
            val group = Group.getGroupEntry(searchResult.token)
            with(group) {
                setRosterOrgId(searchResult.orgKey);setRosterAvatarUrl(searchResult.avatarUrl)
                setRosterDisplayName(searchResult.displayName)
                memberCount = searchResult.numMembers
                setRosterType(Entity.TYPE_GROUP)
                setIsPublic(searchResult.isPublic)
                setMemberSet(searchResult.memberSet)
            }

            //Add metadata if available...
            if (searchResult.metadata != null || !TextUtils.isEmpty(searchResult.description)) {
                try {
                    val metadata = JSONObject()
                    metadata.put(TTConstants.METADATA, searchResult.metadata)
                    metadata.put(TTConstants.DESCRIPTION, searchResult.description)
                    group.ttMetadata = metadata
                } catch (je: JSONException) {
                    Timber.e(je, "onSearchResultsReady")
                }

            }
            return group
        }

        @JvmStatic
        fun getDistListFromSearchEntity(searchResult: SearchResultEntity): DistList {
            val distList = DistList.getDistListEntry(searchResult.token)
            with(distList) {
                distList.totalMembers = searchResult.getReturnField(ReturnField2.TOTAL_MEMBERS) ?: 0
                setRosterOrgId(searchResult.orgKey);setRosterAvatarUrl(searchResult.avatarUrl)
                setRosterDisplayName(searchResult.displayName);setRosterType(Entity.TYPE_DISTLIST)
            }
            return distList
        }

        @JvmStatic
        fun getUniqueMemberIdsSet(group: Group): MutableSet<String> {
            val memberIds: MutableSet<String> = ArraySet(group.memberIdsSet)

            for (set in group.userToProxyMap) {
                if (!set.value.contains(set.key)) {
                    memberIds.remove(set.key)
                }
            }

            return memberIds
        }

    }
}