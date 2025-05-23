/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javafx.scene.control.skin;


import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

import com.sun.javafx.PlatformUtil;
import javafx.animation.FadeTransition;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.StyleOrigin;
import javafx.css.StyleableObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import com.sun.javafx.tk.Toolkit;

/**
 * TableRowSkinBase is the base skin class used by controls such as
 * {@link javafx.scene.control.TableRow} and {@link javafx.scene.control.TreeTableRow}
 * (the concrete classes are {@link TableRowSkin} and {@link TreeTableRowSkin},
 * respectively).
 *
 * @param <T> The type of the cell (i.e. the generic type of the {@link IndexedCell} subclass).
 * @param <C> The cell type (e.g. TableRow or TreeTableRow)
 * @param <R> The type of cell that is contained within each row (e.g.
 *           {@link javafx.scene.control.TableCell} or {@link javafx.scene.control.TreeTableCell}).
 *
 * @since 9
 * @see javafx.scene.control.TableRow
 * @see javafx.scene.control.TreeTableRow
 * @see TableRowSkin
 * @see TreeTableRowSkin
 */
public abstract class TableRowSkinBase<T,
                                       C extends IndexedCell/*<T>*/,
                                       R extends IndexedCell> extends CellSkinBase<C> {

    /* *************************************************************************
     *                                                                         *
     * Static Fields                                                           *
     *                                                                         *
     **************************************************************************/

    // There appears to be a memory leak when using the stub toolkit. Therefore,
    // to prevent tests from failing we disable the animations below when the
    // stub toolkit is being used.
    // Filed as JDK-8120657.
    private static boolean IS_STUB_TOOLKIT = Toolkit.getToolkit().toString().contains("StubToolkit");

    // lets save the CPU and not do animations when on embedded platforms
    private static boolean DO_ANIMATIONS = ! IS_STUB_TOOLKIT && ! PlatformUtil.isEmbedded();

    private static final Duration FADE_DURATION = Duration.millis(200);

    /*
     * This is rather hacky - but it is a quick workaround to resolve the
     * issue that we don't know maximum width of a disclosure node for a given
     * control. If we don't know the maximum width, we have no way to ensure
     * consistent indentation.
     *
     * To work around this, we create a single WeakHashMap to store a max
     * disclosureNode width per TableColumnBase. We use WeakHashMap to help prevent
     * any memory leaks.
     */
    static final Map<TableColumnBase<?,?>, Double> maxDisclosureWidthMap = new WeakHashMap<>();


    /* *************************************************************************
     *                                                                         *
     * Private Fields                                                          *
     *                                                                         *
     **************************************************************************/

    /*
     * A map that maps from TableColumn to TableCell (i.e. model to view).
     * This is recreated whenever the leaf columns change, however to increase
     * efficiency we create cells for all columns, even if they aren't visible,
     * and we only create new cells if we don't already have it cached in this
     * map.
     */
    WeakHashMap<TableColumnBase, Reference<R>> cellsMap;

    // This observableArrayList contains the currently visible table cells for this row.
    final List<R> cells = new ArrayList<>();

    boolean isDirty = false;


    /* *************************************************************************
     *                                                                         *
     * Constructors                                                            *
     *                                                                         *
     **************************************************************************/

    /**
     * Creates a new instance of TableRowSkinBase, although note that this
     * instance does not handle any behavior / input mappings - this needs to be
     * handled appropriately by subclasses.
     *
     * @param control The control that this skin should be installed onto.
     */
    public TableRowSkinBase(C control) {
        super(control);
        getSkinnable().setPickOnBounds(false);

        recreateCells();
        updateCells();

        // init bindings
        // watches for any change in the leaf columns observableArrayList - this will indicate
        // that the column order has changed and that we should update the row
        // such that the cells are in the new order
        registerListChangeListener(getVisibleLeafColumns(), c -> updateLeafColumns());
        // --- end init bindings


        // use invalidation listener here to update even when item equality is true
        // (e.g. see JDK-8098235)
        registerInvalidationListener(control.itemProperty(), o -> requestCellUpdate());
        registerChangeListener(control.indexProperty(), e -> requestCellUpdate());
    }



    /* *************************************************************************
     *                                                                         *
     * Listeners                                                               *
     *                                                                         *
     **************************************************************************/

    void updateLeafColumns() {
        isDirty = true;
        getSkinnable().requestLayout();
    }

    /* *************************************************************************
     *                                                                         *
     * Abstract Methods                                                        *
     *                                                                         *
     **************************************************************************/

    /**
     * Creates a new cell instance that is suitable for representing the given table column instance.
     * @param tc the table column
     * @return the created cell
     */
    protected abstract R createCell(TableColumnBase<T,?> tc);

    /**
     * A method to allow the given cell to be told that it is a member of the given row.
     * How this is implemented is dependent on the actual cell implementation.
     * @param cell The cell for which we want to inform it of its owner row.
     * @param row The row which will be set on the given cell.
     */
    protected abstract void updateCell(R cell, C row);

    /**
     * Returns the {@link TableColumnBase} instance for the given cell instance.
     * @param cell The cell for which a TableColumn is desired.
     * @return the table column
     */
    protected abstract TableColumnBase<T,?> getTableColumn(R cell);

    /**
     * Returns an unmodifiable list containing the currently visible leaf columns.
     * @return the list of visible leaf columns
     */
    protected abstract ObservableList<? extends TableColumnBase/*<T,?>*/> getVisibleLeafColumns();



    /* *************************************************************************
     *                                                                         *
     * Public Methods                                                          *
     *                                                                         *
     **************************************************************************/

    /**
     * Returns the graphic to draw on the inside of the disclosure node. Null
     * is acceptable when no graphic should be shown. Commonly this is the
     * graphic associated with a TreeItem (i.e. treeItem.getGraphic()), rather
     * than a graphic associated with a cell.
     * @return the graphic to draw on the inside of the disclosure node
     */
    protected ObjectProperty<Node> graphicProperty() {
        return null;
    }

    /** {@inheritDoc} */
    @Override protected void layoutChildren(double x, double y, final double w, final double h) {
        checkState();
        if (cellsMap.isEmpty()) return;

        ObservableList<? extends TableColumnBase> visibleLeafColumns = getVisibleLeafColumns();
        if (visibleLeafColumns.isEmpty()) {
            super.layoutChildren(x,y,w,h);
            return;
        }

        C control = getSkinnable();

        //-----------------------------------------
        // indentation code starts here
        //-----------------------------------------
        double leftMargin = 0;
        double disclosureWidth = 0;
        double graphicWidth = 0;
        boolean indentationRequired = isIndentationRequired();
        boolean disclosureVisible = isDisclosureNodeVisible();
        int indentationColumnIndex = 0;
        Node disclosureNode = null;
        if (indentationRequired) {
            // Determine the column in which we want to put the disclosure node.
            // By default it is null, which means the 0th column should be
            // where the indentation occurs.
            TableColumnBase<?,?> treeColumn = getTreeColumn();
            indentationColumnIndex = treeColumn == null ? 0 : visibleLeafColumns.indexOf(treeColumn);
            indentationColumnIndex = indentationColumnIndex < 0 ? 0 : indentationColumnIndex;

            int indentationLevel = getIndentationLevel(control);
            if (! isShowRoot()) indentationLevel--;
            final double indentationPerLevel = getIndentationPerLevel();
            leftMargin = indentationLevel * indentationPerLevel;

            // position the disclosure node so that it is at the proper indent
            final double defaultDisclosureWidth = maxDisclosureWidthMap.containsKey(treeColumn) ?
                maxDisclosureWidthMap.get(treeColumn) : 0;
            disclosureWidth = defaultDisclosureWidth;

            disclosureNode = getDisclosureNode();
            if (disclosureNode != null) {
                disclosureNode.setVisible(disclosureVisible);

                if (disclosureVisible) {
                    disclosureWidth = disclosureNode.prefWidth(h);
                    if (disclosureWidth > defaultDisclosureWidth) {
                        maxDisclosureWidthMap.put(treeColumn, disclosureWidth);

                        // JDK-8094321: The recorded max width of the disclosure node
                        // has increased. We need to go back and request all
                        // earlier rows to update themselves to take into account
                        // this increased indentation.
                        final VirtualFlow<C> flow = getVirtualFlow();
                        for (int i = 0; i < flow.cells.size(); i++) {
                            C cell = flow.cells.get(i);
                            if (cell == null || cell.isEmpty()) continue;
                            cell.requestLayout();
                            cell.layout();
                        }
                    }
                }
            }
        }
        //-----------------------------------------
        // indentation code ends here
        //-----------------------------------------

        // layout the individual column cells
        double width;
        double height;

        /**
         * JDK-8125142:TreeTableView: Vertical Line looks unfinished.
         * We used to not do layout on cells whose row exceeded the number
         * of items, but now we do so as to ensure we get vertical lines
         * where expected in cases where the vertical height exceeds the
         * number of items.
         */
        int index = control.getIndex();
        if (index < 0/* || row >= itemsProperty().get().size()*/) return;

        VirtualFlow<C> virtualFlow = getVirtualFlow();
        double fixedCellSize = getFixedCellSize();
        for (int column = 0, max = cells.size(); column < max; column++) {
            R tableCell = cells.get(column);
            TableColumnBase<T, ?> tableColumn = getTableColumn(tableCell);

            width = snapSizeX(tableColumn.getWidth());

            boolean isVisible = true;
            if (fixedCellSize > 0) {
                // we determine if the cell is visible, and if not we have the
                // ability to take it out of the scenegraph to help improve
                // performance. However, we only do this when there is a
                // fixed cell length specified in the TableView. This is because
                // when we have a fixed cell length it is possible to know with
                // certainty the height of each TableCell - it is the fixed value
                // provided by the developer, and this means that we do not have
                // to concern ourselves with the possibility that the height
                // may be variable and / or dynamic.
                isVisible = isColumnPartiallyOrFullyVisible(x, width, virtualFlow);

                y = 0;
                height = fixedCellSize;
            } else {
                height = h;
            }

            if (isVisible) {
                if (tableCell.getParent() == null) {
                    getChildren().add(tableCell);
                }

                // Added for JDK-8115536, and then updated for JDK-8122708.
                // We change the alignment from CENTER_LEFT to TOP_LEFT if the
                // height of the row is greater than the default size, and if
                // the alignment is the default alignment.
                // What I would rather do is only change the alignment if the
                // alignment has not been manually changed, but for now this will
                // do.
                final boolean centreContent = height <= 24.0;

                // if the style origin is null then the property has not been
                // set (or it has been reset to its default), which means that
                // we can set it without overwriting someone elses settings.
                final StyleOrigin origin = ((StyleableObjectProperty<?>) tableCell.alignmentProperty()).getStyleOrigin();
                if (! centreContent && origin == null) {
                    tableCell.setAlignment(Pos.TOP_LEFT);
                }
                // --- end of JDK-8115536 fix

                //-----------------------------------------
                // further indentation code starts here
                //-----------------------------------------
                if (indentationRequired && column == indentationColumnIndex) {
                    if (disclosureVisible) {
                        double ph = disclosureNode.prefHeight(disclosureWidth);

                        if (width > 0 && width < (disclosureWidth + leftMargin)) {
                            fadeOut(disclosureNode);
                        } else {
                            fadeIn(disclosureNode);
                            disclosureNode.resize(disclosureWidth, ph);

                            disclosureNode.relocate(x + leftMargin,
                                    centreContent ? y + (h / 2.0 - ph / 2.0) :
                                            (y + tableCell.getPadding().getTop()));
                            disclosureNode.toFront();
                        }
                    }

                    // determine starting point of the graphic or cell node, and the
                    // remaining width available to them
                    ObjectProperty<Node> graphicProperty = graphicProperty();
                    Node graphic = graphicProperty == null ? null : graphicProperty.get();

                    if (graphic != null) {
                        graphicWidth = graphic.prefWidth(-1) + 3;
                        double ph = graphic.prefHeight(graphicWidth);

                        if (width > 0 && width < disclosureWidth + leftMargin + graphicWidth) {
                            fadeOut(graphic);
                        } else {
                            fadeIn(graphic);

                            graphic.relocate(x + leftMargin + disclosureWidth,
                                    centreContent ? (h / 2.0 - ph / 2.0) :
                                            (y + tableCell.getPadding().getTop()));

                            graphic.toFront();
                        }
                    }
                }
                //-----------------------------------------
                // further indentation code ends here
                //-----------------------------------------
                tableCell.resize(width, height);
                tableCell.relocate(x, y);

                // Request layout is here as (partial) fix for JDK-8118040.
                // This does not appear to impact performance...
                tableCell.requestLayout();
            } else {
                if (tableCell.getParent() != null) {
                    getChildren().remove(tableCell);
                }
            }

            x += width;
        }
    }

    double getFixedCellSize() {
        return Region.USE_COMPUTED_SIZE;
    }

    int getIndentationLevel(C control) {
        return 0;
    }

    double getIndentationPerLevel() {
        return 0;
    }

    /**
     * Used to represent whether the current virtual flow owner is wanting
     * indentation to be used in this table row.
     */
    boolean isIndentationRequired() {
        return false;
    }

    /**
     * Returns the table column that should show the disclosure nodes and / or
     * a graphic. By default this is the left-most column.
     */
    TableColumnBase getTreeColumn() {
        return null;
    }

    Node getDisclosureNode() {
        return null;
    }

    /**
     * Used to represent whether a disclosure node is visible for _this_
     * table row. Not to be confused with isIndentationRequired(), which is the
     * more general API.
     */
    boolean isDisclosureNodeVisible() {
        return false;
    }

    boolean isShowRoot() {
        return true;
    }

    void updateCells() {
        // if clear isn't called first, we can run into situations where the
        // cells aren't updated properly.
        cells.clear();

        final C skinnable = getSkinnable();
        final int skinnableIndex = skinnable.getIndex();
        final List<? extends TableColumnBase/*<T,?>*/> visibleLeafColumns = getVisibleLeafColumns();

        for (int i = 0, max = visibleLeafColumns.size(); i < max; i++) {
            TableColumnBase<T,?> col = visibleLeafColumns.get(i);

            R cell = null;
            if (cellsMap.containsKey(col)) {
                cell = cellsMap.get(col).get();

                // the reference has been gc'd, remove key entry from map
                if (cell == null) {
                    cellsMap.remove(col);
                }
            }

            if (cell == null) {
                // if the cell is null it means we don't have it in cache and
                // need to create it
                cell = createCellAndCache(col);
            }

            updateCell(cell, skinnable);
            cell.updateIndex(skinnableIndex);
            cells.add(cell);
        }

        getChildren().setAll(cells);
    }

    VirtualFlow<C> getVirtualFlow() {
        Parent p = getSkinnable();
        while (p != null) {
            if (p instanceof VirtualFlow) {
                return (VirtualFlow<C>) p;
            }
            p = p.getParent();
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        double prefWidth = leftInset + rightInset;
        for (R cell : cells) {
            prefWidth += cell.prefWidth(height);
        }
        return prefWidth;
    }

    /** {@inheritDoc} */
    @Override protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        double fixedCellSize = getFixedCellSize();
        if (fixedCellSize > 0) {
            return fixedCellSize;
        }

        // fix for JDK-8118823
        checkState();

        // Support for JDK-8119085: making it easier to specify a height for
        // cells via CSS, where the desired height is less than the height
        // of the TableCells. Essentially, -fx-cell-size is given higher
        // precedence now
        double cellSizeWithInsets = getCellSize() + topInset + bottomInset;
        if (getCellSize() < DEFAULT_CELL_SIZE) {
            return cellSizeWithInsets;
        }

        // FIXME according to profiling, this method is slow and should
        // be optimised
        double prefHeight = 0.0f;
        final int count = cells.size();
        for (int i=0; i<count; i++) {
            final R tableCell = cells.get(i);
            prefHeight = Math.max(prefHeight, tableCell.prefHeight(-1));
        }
        prefHeight += topInset + bottomInset;

        double cellSizeOrMinHeight = Math.max(cellSizeWithInsets, getSkinnable().minHeight(-1));
        double ph = Math.max(prefHeight, cellSizeOrMinHeight);
        return ph;
    }

    /** {@inheritDoc} */
    @Override protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        double fixedCellSize = getFixedCellSize();
        if (fixedCellSize > 0) {
            return fixedCellSize;
        }

        // fix for JDK-8118823
        checkState();

        // Support for JDK-8119085: making it easier to specify a height for
        // cells via CSS, where the desired height is less than the height
        // of the TableCells. Essentially, -fx-cell-size is given higher
        // precedence now
        if (getCellSize() < DEFAULT_CELL_SIZE) {
            return getCellSize() + topInset + bottomInset;
        }

        // FIXME according to profiling, this method is slow and should
        // be optimised
        double minHeight = 0.0f;
        final int count = cells.size();
        for (int i = 0; i < count; i++) {
            final R tableCell = cells.get(i);
            minHeight = Math.max(minHeight, tableCell.minHeight(-1));
        }

        minHeight += topInset + bottomInset;
        return minHeight;
    }

    /** {@inheritDoc} */
    @Override protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        double fixedCellSize = getFixedCellSize();
        if (fixedCellSize > 0) {
            return fixedCellSize;
        }
        return super.computeMaxHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    final void checkState() {
        if (isDirty) {
            updateCells();
            isDirty = false;
        }
    }

    // test-only
    boolean isDirty() {
        return isDirty;
    }

    // test-only
    void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    /* *************************************************************************
     *                                                                         *
     * Private Implementation                                                  *
     *                                                                         *
     **************************************************************************/

    private boolean isColumnPartiallyOrFullyVisible(double start, double width, VirtualFlow<C> virtualFlow) {
        double end = start + width;

        double scrollX = virtualFlow == null ? 0.0 : virtualFlow.getHbar().getValue();
        double headerWidth = virtualFlow == null ? 0.0 : virtualFlow.getViewportBreadth();
        double virtualFlowWidth = headerWidth + scrollX;

        return (start >= scrollX || end > scrollX) && (start < virtualFlowWidth || end <= virtualFlowWidth);
    }

    private void requestCellUpdate() {
        getSkinnable().requestLayout();
        // update the index of all children cells (JDK-8119094).
        // Note that we do this after the TableRow item has been updated,
        // rather than when the TableRow index has changed (as this will be
        // before the row has updated its item). This will result in the
        // issue highlighted in JDK-8115269, where the table cell had the correct
        // item whilst the row had the old item.
        final int newIndex = getSkinnable().getIndex();
        for (int i = 0, max = cells.size(); i < max; i++) {
            cells.get(i).updateIndex(newIndex);
        }
    }

    private void recreateCells() {
        if (cellsMap != null) {
            Collection<Reference<R>> cells = cellsMap.values();
            Iterator<Reference<R>> cellsIter = cells.iterator();
            while (cellsIter.hasNext()) {
                Reference<R> cellRef = cellsIter.next();
                R cell = cellRef.get();
                if (cell != null) {
                    cell.updateIndex(-1);
                    cell.getSkin().dispose();
                    cell.setSkin(null);
                }
            }
            cellsMap.clear();
        }

        ObservableList<? extends TableColumnBase/*<T,?>*/> columns = getVisibleLeafColumns();

        cellsMap = new WeakHashMap<>(columns.size());
        getChildren().clear();

        for (TableColumnBase col : columns) {
            if (cellsMap.containsKey(col)) {
                continue;
            }

            // create a TableCell for this column and store it in the cellsMap
            // for future use
            createCellAndCache(col);
        }
    }

    private R createCellAndCache(TableColumnBase<T,?> col) {
        // we must create a TableCell for this table column
        R cell = createCell(col);

        // and store this in our HashMap until needed
        cellsMap.put(col, new WeakReference<>(cell));

        return cell;
    }

    private void fadeOut(final Node node) {
        if (node.getOpacity() < 1.0) return;

        if (! DO_ANIMATIONS) {
            node.setOpacity(0);
            return;
        }

        final FadeTransition fader = new FadeTransition(FADE_DURATION, node);
        fader.setToValue(0.0);
        fader.play();
    }

    private void fadeIn(final Node node) {
        if (node.getOpacity() > 0.0) return;

        if (! DO_ANIMATIONS) {
            node.setOpacity(1);
            return;
        }

        final FadeTransition fader = new FadeTransition(FADE_DURATION, node);
        fader.setToValue(1.0);
        fader.play();
    }
}
