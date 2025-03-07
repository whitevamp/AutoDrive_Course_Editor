package AutoDriveEditor.Classes;

import AutoDriveEditor.GUI.Buttons.CurveBaseButton;
import AutoDriveEditor.GUI.MapPanel;
import AutoDriveEditor.RoadNetwork.MapNode;

import java.awt.geom.Point2D;
import java.util.LinkedList;

import static AutoDriveEditor.AutoDriveEditor.buttonManager;
import static AutoDriveEditor.AutoDriveEditor.changeManager;
import static AutoDriveEditor.GUI.Buttons.LinerLineBaseButton.*;
import static AutoDriveEditor.GUI.Curves.CurvePanel.*;
import static AutoDriveEditor.GUI.MapPanel.*;
import static AutoDriveEditor.GUI.Menus.DebugMenu.Logging.LogCurveInfoMenu.bDebugLogCurveInfo;
import static AutoDriveEditor.Listeners.MouseListener.prevMousePosX;
import static AutoDriveEditor.Listeners.MouseListener.prevMousePosY;
import static AutoDriveEditor.RoadNetwork.MapNode.*;
import static AutoDriveEditor.RoadNetwork.RoadMap.createMapNode;
import static AutoDriveEditor.RoadNetwork.RoadMap.createNewNetworkNode;
import static AutoDriveEditor.Utils.LoggerUtils.LOG;
import static AutoDriveEditor.Utils.MathUtils.roundUpDoubleToDecimalPlaces;
import static AutoDriveEditor.XMLConfig.AutoSave.resumeAutoSaving;
import static AutoDriveEditor.XMLConfig.AutoSave.suspendAutoSaving;
import static AutoDriveEditor.XMLConfig.EditorXML.*;

public class QuarticCurve {

    public final LinkedList<MapNode> curveNodesList;
    private MapNode curveStartNode;
    private MapNode curveEndNode;
    private MapNode controlPoint1;
    private MapNode controlPoint2;
    private MapNode controlPoint3;
    
    private final Point2D.Double virtualControlPoint1;
    private final Point2D.Double virtualControlPoint2;
    private final Point2D.Double virtualControlPoint3;
    
    private int numInterpolationPoints;
    private int nodeType;
    private boolean isReversePath;
    private boolean isDualPath;

    public QuarticCurve(MapNode startNode, MapNode endNode) {
        this.curveNodesList = new LinkedList<>();
        this.curveStartNode = startNode;
        this.curveEndNode = endNode;
        this.numInterpolationPoints = numIterationsSlider.getValue();
        if (this.numInterpolationPoints < 2) this.numInterpolationPoints = 2;

        // Create three control points. Adjust initial positions as needed.
        this.controlPoint1 = createMapNode(0, startNode.x, 0, endNode.z, NODE_FLAG_CONTROL_POINT, false, true);
        this.controlPoint2 = createMapNode(1, (startNode.x + endNode.x) / 2, 0, (startNode.z + endNode.z) / 2, NODE_FLAG_CONTROL_POINT, false, true);
        this.controlPoint3 = createMapNode(2, endNode.x, 0, startNode.z, NODE_FLAG_CONTROL_POINT, false, true);

        this.virtualControlPoint1 = new Point2D.Double(controlPoint1.x, controlPoint1.z);
        this.virtualControlPoint2 = new Point2D.Double(controlPoint2.x, controlPoint2.z);
        this.virtualControlPoint3 = new Point2D.Double(controlPoint3.x, controlPoint3.z);
        this.isReversePath = curvePathReverse.isSelected();
        this.isDualPath = curvePathDual.isSelected();
        this.nodeType = curvePathRegular.isSelected() ? NODE_FLAG_REGULAR : NODE_FLAG_SUBPRIO;
        this.updateCurve();
        curveOptionsPanel.setVisible(true);
    }

    public void getInterpolationPointsForCurve(MapNode startNode, MapNode endNode) {
        if (startNode == null || endNode == null || this.numInterpolationPoints < 1) return;
        double step = 1.0 / (double)this.numInterpolationPoints;
        curveNodesList.clear();

        // Add the starting node
        curveNodesList.add(curveStartNode);

        int id = 0;
        // Calculate all in-between points using the quartic Bézier formula.
        // Note: using (i+step < 1.0001) to account for rounding errors.
        for (double i = step; i + step < 1.0001; i += step) {
            Point2D.Double point = calcPointsForCurve(startNode, endNode,
                    this.virtualControlPoint1.x, this.virtualControlPoint1.y,
                    this.virtualControlPoint2.x, this.virtualControlPoint2.y,
                    this.virtualControlPoint3.x, this.virtualControlPoint3.y, i);
            MapNode newNode = createMapNode(id, point.getX(), -1, point.getY(), this.nodeType, false, false);
            curveNodesList.add(newNode);
            if (i + step >= 1.0001) {
                LOG.info("WARNING -- last node was not calculated, this should not happen!! -- step = {} ", i + step);
            }
            id++;
        }
        // Add the end node to complete the curve
        curveNodesList.add(curveEndNode);
    }

    /**
     * Calculates a point on the quartic Bézier curve using control points.
     *
     * Formula:
     * B(t) = (1-t)^4 * P0 + 4*(1-t)^3*t * CP1 + 6*(1-t)^2*t^2 * CP2 +
     *        4*(1-t)*t^3 * CP3 + t^4 * P4
     *
     * @param startNode the start MapNode (P0)
     * @param endNode the end MapNode (P4)
     * @param cp1x x-coordinate for control point 1
     * @param cp1z z-coordinate for control point 1
     * @param cp2x x-coordinate for control point 2
     * @param cp2z z-coordinate for control point 2
     * @param cp3x x-coordinate for control point 3
     * @param cp3z z-coordinate for control point 3
     * @param t parameter between 0 and 1
     * @return the calculated point as a Point2D.Double (x, z)
     */
    public Point2D.Double calcPointsForCurve(MapNode startNode, MapNode endNode,
                                               double cp1x, double cp1z,
                                               double cp2x, double cp2z,
                                               double cp3x, double cp3z,
                                               double t) {
        double oneMinusT = 1 - t;
        double oneMinusT2 = Math.pow(oneMinusT, 2);
        double oneMinusT3 = Math.pow(oneMinusT, 3);
        double oneMinusT4 = Math.pow(oneMinusT, 4);
        double t2 = Math.pow(t, 2);
        double t3 = Math.pow(t, 3);
        double t4 = Math.pow(t, 4);

        Point2D.Double point = new Point2D.Double();
        point.x = oneMinusT4 * startNode.x +
                  4 * oneMinusT3 * t * cp1x +
                  6 * oneMinusT2 * t2 * cp2x +
                  4 * oneMinusT * t3 * cp3x +
                  t4 * endNode.x;
        point.y = oneMinusT4 * startNode.z +
                  4 * oneMinusT3 * t * cp1z +
                  6 * oneMinusT2 * t2 * cp2z +
                  4 * oneMinusT * t3 * cp3z +
                  t4 * endNode.z;
        return point;
    }

    public void updateCurve() {
        if (this.curveStartNode != null && this.curveEndNode != null && this.numInterpolationPoints >= 1) {
            getInterpolationPointsForCurve(this.curveStartNode, this.curveEndNode);
        }
    }

    public void commitCurve() {
        suspendAutoSaving();

        LinkedList<MapNode> mergeNodesList = new LinkedList<>();
        mergeNodesList.add(curveStartNode);

        if (this.curveStartNode.y != -1 && this.curveEndNode.y == -1) {
            this.curveEndNode.y = this.curveStartNode.y;
        }
        if (this.curveEndNode.y != -1 && this.curveStartNode.y == -1) {
            this.curveStartNode.y = this.curveEndNode.y;
        }

        float yInterpolation = (float) ((curveEndNode.y - curveStartNode.y) / (this.curveNodesList.size() - 1));
        for (int j = 1; j < curveNodesList.size() - 1; j++) {
            MapNode tempNode = curveNodesList.get(j);
            double heightMapY = curveStartNode.y + (yInterpolation * j);
            MapNode newNode = createNewNetworkNode(tempNode.x, heightMapY, tempNode.z, this.nodeType, false, false);
            mergeNodesList.add(newNode);
        }
        mergeNodesList.add(curveEndNode);
        changeManager.addChangeable(new CurveBaseButton.CurveChanger(mergeNodesList, isReversePath, isDualPath));
        connectNodes(mergeNodesList, isReversePath, isDualPath);
        resumeAutoSaving();

        if (bDebugLogCurveInfo) LOG.info("QuarticCurve created {} nodes", mergeNodesList.size() - 2);
    }

    public static void connectNodes(LinkedList<MapNode> mergeNodesList, boolean reversePath, boolean dualPath) {
        for (int j = 0; j < mergeNodesList.size() - 1; j++) {
            MapNode startNode = mergeNodesList.get(j);
            MapNode endNode = mergeNodesList.get(j + 1);
            if (reversePath) {
                MapPanel.createConnectionBetween(startNode, endNode, CONNECTION_REVERSE);
            } else if (dualPath) {
                MapPanel.createConnectionBetween(startNode, endNode, CONNECTION_DUAL);
            } else {
                MapPanel.createConnectionBetween(startNode, endNode, CONNECTION_STANDARD);
            }
        }
    }

    public void clear() {
        this.curveNodesList.clear();
        this.controlPoint1 = null;
        this.controlPoint2 = null;
        this.controlPoint3 = null;
        this.curveStartNode = null;
        this.curveEndNode = null;
    }

    public void updateVirtualControlPoint1(double diffX, double diffY) {
        if (buttonManager.getCurrentButtonID().equals("Quartic")) {
            this.virtualControlPoint1.x += diffX * controlPointMoveScaler;
            this.virtualControlPoint1.y += diffY * controlPointMoveScaler;
        } else {
            this.virtualControlPoint1.x += diffX;
            this.virtualControlPoint1.y += diffY;
        }
        this.updateCurve();
    }

    public void updateVirtualControlPoint2(double diffX, double diffY) {
        if (buttonManager.getCurrentButtonID().equals("Quartic")) {
            this.virtualControlPoint2.x += diffX * controlPointMoveScaler;
            this.virtualControlPoint2.y += diffY * controlPointMoveScaler;
        } else {
            this.virtualControlPoint2.x += diffX;
            this.virtualControlPoint2.y += diffY;
        }
        this.updateCurve();
    }

    public void updateVirtualControlPoint3(double diffX, double diffY) {
        if (buttonManager.getCurrentButtonID().equals("Quartic")) {
            this.virtualControlPoint3.x += diffX * controlPointMoveScaler;
            this.virtualControlPoint3.y += diffY * controlPointMoveScaler;
        } else {
            this.virtualControlPoint3.x += diffX;
            this.virtualControlPoint3.y += diffY;
        }
        this.updateCurve();
    }

    public void updateControlPoint1(double diffX, double diffY) {
        this.controlPoint1.x = roundUpDoubleToDecimalPlaces(this.controlPoint1.x + diffX, 3);
        this.controlPoint1.z = roundUpDoubleToDecimalPlaces(this.controlPoint1.z + diffY, 3);
        updateVirtualControlPoint1(diffX, diffY);
        updateCurve();
    }

    public void updateControlPoint2(double diffX, double diffY) {
        this.controlPoint2.x = roundUpDoubleToDecimalPlaces(this.controlPoint2.x + diffX, 3);
        this.controlPoint2.z = roundUpDoubleToDecimalPlaces(this.controlPoint2.z + diffY, 3);
        updateVirtualControlPoint2(diffX, diffY);
        updateCurve();
    }

    public void updateControlPoint3(double diffX, double diffY) {
        this.controlPoint3.x = roundUpDoubleToDecimalPlaces(this.controlPoint3.x + diffX, 3);
        this.controlPoint3.z = roundUpDoubleToDecimalPlaces(this.controlPoint3.z + diffY, 3);
        updateVirtualControlPoint3(diffX, diffY);
        updateCurve();
    }

    public void moveControlPoint1(double diffX, double diffY) {
        Point2D point = calcScaledDifference(this.controlPoint1, diffX, diffY);
        this.controlPoint1.x = roundUpDoubleToDecimalPlaces(this.controlPoint1.x + point.getX(), 3);
        this.controlPoint1.z = roundUpDoubleToDecimalPlaces(this.controlPoint1.z + point.getY(), 3);
        updateVirtualControlPoint1(point.getX(), point.getY());
        updateCurve();
    }

    public void moveControlPoint2(double diffX, double diffY) {
        Point2D point = calcScaledDifference(this.controlPoint2, diffX, diffY);
        this.controlPoint2.x = roundUpDoubleToDecimalPlaces(this.controlPoint2.x + point.getX(), 3);
        this.controlPoint2.z = roundUpDoubleToDecimalPlaces(this.controlPoint2.z + point.getY(), 3);
        updateVirtualControlPoint2(point.getX(), point.getY());
        updateCurve();
    }

    public void moveControlPoint3(double diffX, double diffY) {
        Point2D point = calcScaledDifference(this.controlPoint3, diffX, diffY);
        this.controlPoint3.x = roundUpDoubleToDecimalPlaces(this.controlPoint3.x + point.getX(), 3);
        this.controlPoint3.z = roundUpDoubleToDecimalPlaces(this.controlPoint3.z + point.getY(), 3);
        updateVirtualControlPoint3(point.getX(), point.getY());
        updateCurve();
    }

    private Point2D calcScaledDifference(MapNode node, double diffX, double diffY) {
        double scaledDiffX;
        double scaledDiffY;
        if (bGridSnap) {
            Point2D p = screenPosToWorldPos((int) (prevMousePosX + diffX), (int) (prevMousePosY + diffY));
            double newX, newY;
            if (bGridSnapSubs) {
                newX = Math.round(p.getX() / (gridSpacingX / (gridSubDivisions + 1))) * (gridSpacingX / (gridSubDivisions + 1));
                newY = Math.round(p.getY() / (gridSpacingY / (gridSubDivisions + 1))) * (gridSpacingY / (gridSubDivisions + 1));
            } else {
                newX = Math.round(p.getX() / gridSpacingX) * gridSpacingX;
                newY = Math.round(p.getY() / gridSpacingY) * gridSpacingY;
            }
            scaledDiffX = newX - node.x;
            scaledDiffY = newY - node.z;
        } else {
            scaledDiffX = roundUpDoubleToDecimalPlaces((diffX * mapScale) / zoomLevel, 3);
            scaledDiffY = roundUpDoubleToDecimalPlaces((diffY * mapScale) / zoomLevel, 3);
        }
        return new Point2D.Double(scaledDiffX, scaledDiffY);
    }

    public boolean isReversePath() { 
        return isReversePath; 
    }

    public boolean isDualPath() { 
        return isDualPath; 
    }

    public boolean isCurveAnchorPoint(MapNode node) { 
        return node == this.curveStartNode || node == this.curveEndNode; 
    }

    public boolean isControlPoint(MapNode node) { 
        return node == this.controlPoint1 || node == this.controlPoint2 || node == this.controlPoint3; 
    }

    // Getters
    public int getNodeType() { 
        return this.nodeType; 
    }

    public LinkedList<MapNode> getCurveNodes() { 
        return this.curveNodesList; 
    }

    public MapNode getCurveStartNode() { 
        return this.curveStartNode; 
    }

    public MapNode getCurveEndNode() { 
        return this.curveEndNode; 
    }

    public MapNode getControlPoint1() { 
        return this.controlPoint1; 
    }

    public MapNode getControlPoint2() { 
        return this.controlPoint2; 
    }

    public MapNode getControlPoint3() { 
        return this.controlPoint3; 
    }

    // Setters
    public void setReversePath(boolean isSelected) {
        this.isReversePath = isSelected;
    }

    public void setDualPath(boolean isSelected) {
        this.isDualPath = isSelected;
    }

    public void setNodeType(int nodeType) {
        this.nodeType = nodeType;
        if (nodeType == NODE_FLAG_SUBPRIO) {
            for (int j = 1; j < curveNodesList.size() - 1; j++) {
                MapNode tempNode = curveNodesList.get(j);
                tempNode.flag = 1;
            }
        } else {
            for (int j = 1; j < curveNodesList.size() - 1; j++) {
                MapNode tempNode = curveNodesList.get(j);
                tempNode.flag = 0;
            }
        }
    }

    public void setNumInterpolationPoints(int points) {
        this.numInterpolationPoints = points;
        if (this.curveStartNode != null && this.curveEndNode != null) {
            getInterpolationPointsForCurve(this.curveStartNode, this.curveEndNode);
        }
    }

    public void setCurveStartNode(MapNode curveStartNode) {
        this.curveStartNode = curveStartNode;
        this.updateCurve();
    }

    public void setCurveEndNode(MapNode curveEndNode) {
        this.curveEndNode = curveEndNode;
        this.updateCurve();
    }
}
