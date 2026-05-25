package com.github.tvbox.osc.ktv;

import java.util.List;

public interface MediaLibraryProvider {
    List<KtvMediaEntry> list(String path) throws Exception;

    String resolvePlayableUrl(KtvMediaEntry entry) throws Exception;

    boolean supportsRecursiveScan();
}
