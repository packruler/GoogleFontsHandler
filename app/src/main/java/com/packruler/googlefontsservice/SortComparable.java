package com.packruler.googlefontsservice;

import java.util.Comparator;

/**
 * Created by Packruler on 3/5/2015.
 */
public class SortComparable implements Comparator {

    private String sort;

    public SortComparable(String sort) {
        this.sort = sort;
    }

    @Override
    public int compare(Object lhs, Object rhs) {
        if (!(lhs instanceof LocalWebfont && rhs instanceof LocalWebfont))
            throw new RuntimeException("Non LocalWebfonts. Unable to sort");

        if (sort.equals(LocalWebfont.TRENDING) || sort.equals(LocalWebfont.STYLE) || sort.equals(LocalWebfont.POPULARITY))
            return ((LocalWebfont) lhs).getRatings().get(sort) - ((LocalWebfont) rhs).getRatings().get(sort);

        if (sort.equals(LocalWebfont.DATE))
            return (int) (((LocalWebfont) rhs).getLastModified().getValue() - ((LocalWebfont) lhs).getLastModified().getValue());

        return ((LocalWebfont) lhs).getFamily().compareTo(((LocalWebfont) rhs).getFamily());
    }
}
