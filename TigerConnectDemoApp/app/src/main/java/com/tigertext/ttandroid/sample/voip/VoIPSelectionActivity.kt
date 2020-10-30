package com.tigertext.ttandroid.sample.voip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.utils.TTCallUtils

class VoIPSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        if (extras != null) {
            showCallOptions(extras)
        } else {
            finish()
        }
    }

    private fun showCallOptions(extras: Bundle) {
        //TODO this is called again in VoIPCallActivity setupInitState
        val callInfo = VoIPManager.getCallInfoFromExtras(extras)
        val options = extras.getIntegerArrayList(EXTRA_CALL_TYPE_OPTIONS)?.toSet() ?: calculateCallTypes(this, callInfo)

        val (arguments, numberOfOptions) = getCallOptions(this, options)
        if (numberOfOptions == 0) {
            finish()
            return
        } else if (numberOfOptions == 1) {
            when (val id = arguments.getIntegerArrayList(BottomSheetSelectionDialog.ARRAY_OF_IDS)!![0]) {
                VOIP_VIDEO_ID, VOIP_CALL_ID -> handleCallSelection(id == VOIP_VIDEO_ID, extras)
                VOIP_C2C_ID -> {
//                    startActivity(TTIntentUtils.getClickToCallIntent(this, callInfo))
                    finish()
                }
            }
            return
        }

        val bottomSheetDialogFragment = BottomSheetSelectionDialog()
        bottomSheetDialogFragment.arguments = arguments
        bottomSheetDialogFragment.setOnItemSelected(object : BottomSheetSelectionDialog.BottomDialogItemClick {
            override fun onNothingSelected() {
                finish()
            }

            override fun onItemSelected(view: View, position: Int, item: BottomSheetSelectionDialog.BottomDialogItem) {
                when (item.id) {
                    VOIP_VIDEO_ID, VOIP_CALL_ID -> handleCallSelection(item.id == VOIP_VIDEO_ID, extras)
                    VOIP_C2C_ID -> {
//                        startActivity(TTIntentUtils.getClickToCallIntent(this@VoIPSelectionActivity, callInfo))
                        finish()
                    }
                }
            }
        })

        bottomSheetDialogFragment.show(supportFragmentManager, BottomSheetSelectionDialog.TAG)
    }

    private fun calculateCallTypes(context: Context, callInfo: VoIPManager.CallInfo): Set<Int> {
        val types = mutableSetOf<Int>()

        val isGroup = !callInfo.groupId.isNullOrEmpty()

        val organization = TT.getInstance().organizationManager.getOrganization(callInfo.orgId)
        if (!isGroup) {
            if (TTCallUtils.isCapableOfClickToCall(organization, context)) {
                types.add(VOIP_C2C_ID)
            }
            if (TTCallUtils.isCapableOfP2pVoipAudio(organization)) {
                types.add(VOIP_CALL_ID)
            }
            if (TTCallUtils.isCapableOfP2pVoipVideo(organization)) {
                types.add(VOIP_VIDEO_ID)
            }
        } else {
            if (TTCallUtils.isCapableOfGroupVoipAudio(organization)) {
                types.add(VOIP_CALL_ID)
            }
            if (TTCallUtils.isCapableOfGroupVoipVideo(organization)) {
                types.add(VOIP_VIDEO_ID)
            }
        }

        return types
    }

    private fun getCallOptions(context: Context, callTypes: Set<Int>): OptionsResult {
        val voiceLabel = context.getString(R.string.bottom_option_voip_label)
        val c2cLabel = context.getString(R.string.bottom_option_c2c_label)
        val videoLabel = context.getString(R.string.bottom_option_video_label)

        val labels = mutableListOf<String>()
        val icons = mutableListOf<Int>()
        val ids = mutableListOf<Int>()

        if (callTypes.contains(VOIP_C2C_ID)) {
            labels.add(c2cLabel);icons.add(R.drawable.ic_round_call);ids.add(VOIP_C2C_ID)
        }
        if (callTypes.contains(VOIP_CALL_ID)) {
            labels.add(voiceLabel);icons.add(R.drawable.ic_round_call);ids.add(VOIP_CALL_ID)
        }
        if (callTypes.contains(VOIP_VIDEO_ID)) {
            labels.add(videoLabel);icons.add(R.drawable.ic_video_camera_on);ids.add(VOIP_VIDEO_ID)
        }

        return OptionsResult(BottomSheetSelectionDialog.getArguments(labels, icons, ids), labels.size)
    }

    data class OptionsResult(val bundle: Bundle, val numberOfOptions: Int)

    fun handleCallSelection(isVideoCall: Boolean, extras: Bundle) {
        val intent = Intent(this, VoIPCallActivity::class.java)
        intent.putExtras(extras)
        intent.putExtra(VoIPCallActivity.EXTRA_CALL_TYPE, if (isVideoCall) VoIPCallActivity.CALL_TYPE_VIDEO else VoIPCallActivity.CALL_TYPE_AUDIO)
        startActivity(intent)
        finish()
    }

    companion object {
        // Call choice options in bottom selection fragment
        const val VOIP_CALL_ID = 0x0
        const val VOIP_VIDEO_ID = 0x1
        const val VOIP_C2C_ID = 0x2

        const val EXTRA_CALL_TYPE_OPTIONS = "call_type_options"
    }
}