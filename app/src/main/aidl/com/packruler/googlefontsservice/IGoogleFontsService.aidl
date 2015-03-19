// IGoogleFontsService.aidl
package com.packruler.googlefontsservice;

import java.util.Map;
import com.packruler.googlefontsservice.LocalWebfont;

// Declare any non-default types here with import statements

interface IGoogleFontsService {

    Map updateSort(String sortOrder);

    Map updateFonts();

    Map getAllFonts();

    List getAllFontList(String sort);

    Map getSerif();

    List getSerifList(String sort);

    Map getSansSerif();

    List getSansSerifList(String sort);

    Map getDisplay();

    List getDisplayList(String sort);

    Map getHandwriting();

    List getHandwritingList(String sort);

    Map getMonospace();

    List getMonospaceList(String sort);

    void deleteAllFonts();

    String getFontFilePath(String family, String variant);

    String getSortOrder();

    String getFontDirPath();
}
