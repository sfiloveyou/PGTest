package com.sf.pg.mapper;
import java.nio.channels.SelectionKey;

/**
 * Created with IntelliJ IDEA.
 * User: chervanev
 * Date: 17.01.13
 * Time: 16:38
 */
public interface IHandler {
    boolean canHandle(SelectionKey selectionKey);
    void perform(SelectionKey selectionKey);
}
