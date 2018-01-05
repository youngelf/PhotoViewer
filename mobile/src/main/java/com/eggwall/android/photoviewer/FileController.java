package com.eggwall.android.photoviewer;

/**
 * Controls access to files and allows next/previous access to files
 */
public class FileController {

    /**
     * Sets the current directory to the name given here. The name is relative to the gallery
     * directory.
     * @param relativeDirectoryName
     * @return whether setting the directory was a success
     */
    boolean setDirectory(String relativeDirectoryName) {
        return false;
    }

    /**
     * Requests adding a URI as a gallery.
     * @param location
     * @return
     */
    boolean addUri (String location) {
        return false;
    }

    /**
     * Returns an array of all galleries in the default location.
     * @return
     */
    String[] getAllGalleries () {
        return null;
    }
}
