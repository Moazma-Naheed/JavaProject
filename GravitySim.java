package com.mycompany.gravitysim;
package gravitysim; // Agar aapke package ka naam kuch aur hai to wo pehli line mein rehne dein

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

// --- 1. Core Body Class ---
class Body {
    double mass, radius, x, y, vx, vy, ax, ay;
    Color color;
    LinkedList<Point> trail = new LinkedList<>();
    private final int MAX_TRAIL = 100;

    public Body(double mass, double radius, double x, double y, double vx, double vy, Color color) {
        this.mass = mass; this.radius = radius; this.x = x; this.y = y;
        this.vx = vx; this.vy = vy; this.color = color;
    }

    public void updateTrail() {
        trail.add(new Point((int) x, (int) y));
        if (trail.size() > MAX_TRAIL) trail.removeFirst();
    }
}

// --- 2. Physics Integrator ---
interface Integrator {
    void step(List<Body> bodies, double dt, boolean useRelativity);
}

class LeapfrogIntegrator implements Integrator {
    private final double G = 6.674e-11;

    public void step(List<Body> bodies, double dt, boolean useRelativity) {
        double factor = useRelativity ? 1.5 : 1.0;
        for (Body b : bodies) {
            b.x += b.vx * dt + 0.5 * b.ax * dt * dt;
            b.y += b.vy * dt + 0.5 * b.ay * dt * dt;
        }
        for (Body b1 : bodies) {
            double oldAx = b1.ax, oldAy = b1.ay;
            double totalAx = 0, totalAy = 0;
            for (Body b2 : bodies) {
                if (b1 == b2) continue;
                double dx = b2.x - b1.x, dy = b2.y - b1.y;
                double distSq = dx * dx + dy * dy + 200; 
                double dist = Math.sqrt(distSq);
                double force = (G * b1.mass * b2.mass * factor) / distSq;
                totalAx += (force * dx / dist) / b1.mass;
                totalAy += (force * dy / dist) / b1.mass;
            }
            b1.ax = totalAx; b1.ay = totalAy;
            b1.vx += 0.5 * (oldAx + b1.ax) * dt;
            b1.vy += 0.5 * (oldAy + b1.ay) * dt;
        }
    }
}

// --- 3. Universe Panel ---
class Universe extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {
    private List<Body> bodies = new ArrayList<>();
    private final Integrator integrator = new LeapfrogIntegrator();
    private double zoom = 1.0;
    private boolean useRelativity = false;
    private Point clickStart, currentDrag;
    private double nextMass = 1e12;

    public Universe() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(1000, 700));
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        resetToBinary();
    }

    public void resetToBinary() {
        bodies.clear();
        bodies.add(new Body(1e14, 15, 400, 350, 0, 10, Color.YELLOW));
        bodies.add(new Body(1e14, 15, 600, 350, 0, -10, Color.ORANGE));
    }

    public void addRandomCluster() {
        for (int i = 0; i < 25; i++) {
            bodies.add(new Body(Math.random() * 5e12, 6, Math.random() * 800 + 100, 
                Math.random() * 500 + 100, Math.random() * 10 - 5, Math.random() * 10 - 5, Color.WHITE));
        }
    }

    public void setNextMass(double mass) { this.nextMass = mass; }
    public void setRelativity(boolean val) { this.useRelativity = val; }

    public void update() {
        integrator.step(bodies, 0.2, useRelativity);
        List<Body> toRemove = new ArrayList<>();
        for (int i = 0; i < bodies.size(); i++) {
            for (int j = i + 1; j < bodies.size(); j++) {
                Body b1 = bodies.get(i); Body b2 = bodies.get(j);
                if (toRemove.contains(b1) || toRemove.contains(b2)) continue;
                double dx = b2.x - b1.x, dy = b2.y - b1.y;
                if (Math.sqrt(dx*dx + dy*dy) < (b1.radius + b2.radius)) {
                    b1.vx = (b1.vx * b1.mass + b2.vx * b2.mass) / (b1.mass + b2.mass);
                    b1.vy = (b1.vy * b1.mass + b2.vy * b2.mass) / (b1.mass + b2.mass);
                    b1.mass += b2.mass;
                    b1.radius = Math.min(35, b1.radius + 1);
                    toRemove.add(b2);
                }
            }
        }
        bodies.removeAll(toRemove);
        for (Body b : bodies) b.updateTrail();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform at = g2.getTransform();
        g2.scale(zoom, zoom);
        
        if (clickStart != null && currentDrag != null) {
            g2.setColor(Color.RED);
            g2.drawLine(clickStart.x, clickStart.y, currentDrag.x, currentDrag.y);
        }
        
        for (Body b : bodies) {
            g2.setColor(new Color(b.color.getRed(), b.color.getGreen(), b.color.getBlue(), 40));
            for (int i = 1; i < b.trail.size(); i++) {
                Point p1 = b.trail.get(i - 1); Point p2 = b.trail.get(i);
                g2.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
            g2.setColor(b.color);
            g2.fill(new Ellipse2D.Double(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2));
        }
        g2.setTransform(at);
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.getWheelRotation() < 0) zoom *= 1.1; else zoom /= 1.1;
    }
    public void mousePressed(MouseEvent e) { clickStart = e.getPoint(); currentDrag = e.getPoint(); }
    public void mouseDragged(MouseEvent e) { currentDrag = e.getPoint(); }
    public void mouseReleased(MouseEvent e) {
        if (clickStart != null) {
            double vx = (clickStart.x - e.getX()) / (4.0 * zoom);
            double vy = (clickStart.y - e.getY()) / (4.0 * zoom);
            bodies.add(new Body(nextMass, 6, clickStart.x/zoom, clickStart.y/zoom, vx, vy, Color.CYAN));
            clickStart = null;
        }
    }
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mouseMoved(MouseEvent e) {}
}

// --- 4. Main Frame ---
public class GravitySim extends JFrame {
    public GravitySim() {
        Universe universe = new Universe();
        JPanel controls = new JPanel();
        controls.setBackground(new Color(40, 40, 40));
        JButton resetBtn = new JButton("Reset");
        JButton clusterBtn = new JButton("Add Cluster");
        JCheckBox relBox = new JCheckBox("Relativity");
        relBox.setForeground(Color.WHITE);
        relBox.setOpaque(false);
        relBox.addActionListener(e -> universe.setRelativity(relBox.isSelected()));
        JSlider massSlider = new JSlider(1, 10, 5);
        massSlider.setBackground(new Color(40, 40, 40));
        massSlider.addChangeListener(e -> universe.setNextMass(Math.pow(10, massSlider.getValue() + 9)));
        
        controls.add(resetBtn); controls.add(clusterBtn);
        controls.add(new JLabel("<html><font color='white'>Mass:</font></html>"));
        controls.add(massSlider); controls.add(relBox);
        
        resetBtn.addActionListener(e -> universe.resetToBinary());
        clusterBtn.addActionListener(e -> universe.addRandomCluster());
        
        this.setLayout(new BorderLayout());
        this.add(universe, BorderLayout.CENTER);
        this.add(controls, BorderLayout.SOUTH);
        this.pack();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);
        new Timer(16, e -> { universe.update(); universe.repaint(); }).start();
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new GravitySim().setVisible(true)); }
}