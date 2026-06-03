package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.ui.activity.PlayActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class DrivePlayHelper {

    public static void playFile(Activity activity, String title, String fileUrl) {
        playFile(activity, title, fileUrl, null);
    }

    public static void playFile(Activity activity, String title, String fileUrl, String playerConfigJson) {
        playFileList(activity, title,
                Collections.singletonList(fileUrl),
                Collections.singletonList(fileUrl),
                0, playerConfigJson);
    }

    public static void playFileList(Activity activity, String title,
                                    List<String> fileNames, List<String> fileUrls,
                                    int startIndex, String playerConfigJson) {
        if (fileUrls == null || fileUrls.isEmpty()) return;
        VodInfo vodInfo = new VodInfo();
        vodInfo.name = title;
        vodInfo.playFlag = "drive";
        vodInfo.playerCfg = playerConfigJson;
        vodInfo.seriesFlags = new ArrayList<>();
        vodInfo.seriesFlags.add(new VodInfo.VodSeriesFlag("drive"));
        vodInfo.seriesMap = new LinkedHashMap<>();
        List<VodInfo.VodSeries> seriesList = new ArrayList<>();
        for (int i = 0; i < fileUrls.size(); i++) {
            VodInfo.VodSeries series = new VodInfo.VodSeries(fileNames.get(i), "tvbox-drive://" + fileUrls.get(i));
            seriesList.add(series);
        }
        vodInfo.seriesMap.put("drive", seriesList);
        vodInfo.playGroupCount = seriesList.size();
        vodInfo.playGroup = 0;
        vodInfo.playIndex = startIndex;
        Bundle bundle = new Bundle();
        bundle.putBoolean("newSource", true);
        bundle.putString("sourceKey", "_drive");
        bundle.putSerializable("VodInfo", vodInfo);
        Intent intent = new Intent(activity, PlayActivity.class);
        intent.putExtras(bundle);
        activity.startActivity(intent);
    }
}
