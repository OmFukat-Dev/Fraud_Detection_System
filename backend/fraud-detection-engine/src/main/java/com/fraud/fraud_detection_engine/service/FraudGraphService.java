package com.fraud.fraud_detection_engine.service;

import com.fraud.fraud_detection_engine.dto.FraudGraphChain;
import com.fraud.fraud_detection_engine.dto.FraudGraphPattern;
import com.fraud.fraud_detection_engine.dto.FraudGraphResponse;
import com.fraud.fraud_detection_engine.dto.FraudGraphRing;
import com.fraud.fraud_detection_engine.model.Transaction;
import com.fraud.fraud_detection_engine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudGraphService {

    private final TransactionRepository transactionRepository;

    public FraudGraphResponse buildGraphAnalysis(int limit) {
        int safeLimit = Math.max(1, limit);
        List<Transaction> transactions = transactionRepository.findAll();

        if (transactions.isEmpty()) {
            return emptyResponse();
        }

        Map<String, NodeStats> nodes = new LinkedHashMap<>();
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        Map<String, Double> transactionScores = new LinkedHashMap<>();
        Map<String, Transaction.FraudVerdict> transactionVerdicts = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            if (tx.getTransactionId() == null || tx.getUserId() == null || tx.getUserId().isBlank()) {
                continue;
            }

            String transactionId = tx.getTransactionId();
            double fraudScore = tx.getFraudScore() != null ? tx.getFraudScore() : 0.0;
            boolean flagged = isFlagged(tx.getFraudVerdict());

            transactionScores.put(transactionId, fraudScore);
            transactionVerdicts.put(transactionId, tx.getFraudVerdict());

            String userKey = nodeKey(NodeType.USER, tx.getUserId());
            NodeStats userNode = ensureNode(nodes, userKey, NodeType.USER, tx.getUserId());
            recordNodeActivity(userNode, tx, flagged, fraudScore);

            addLinkedEntity(nodes, adjacency, userKey, NodeType.MERCHANT, tx.getMerchantId(), tx, flagged, fraudScore);
            addLinkedEntity(nodes, adjacency, userKey, NodeType.DEVICE, tx.getDeviceId(), tx, flagged, fraudScore);
            addLinkedEntity(nodes, adjacency, userKey, NodeType.IP, tx.getIpAddress(), tx, flagged, fraudScore);
            addLinkedEntity(nodes, adjacency, userKey, NodeType.LOCATION, locationBucket(tx.getLatitude(), tx.getLongitude()), tx, flagged, fraudScore);
        }

        List<Component> components = buildComponents(nodes, adjacency);

        List<FraudGraphRing> rings = new ArrayList<>();
        int ringIndex = 1;
        for (Component component : components) {
            FraudGraphRing ring = toRing(component, nodes, transactionScores, transactionVerdicts, ringIndex);
            if (ring != null) {
                rings.add(ring);
                ringIndex++;
            }
        }

        rings = rings.stream()
                .sorted(Comparator.comparingInt(FraudGraphRing::getFlaggedTransactionCount).reversed()
                        .thenComparingInt(FraudGraphRing::getUserCount).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());

        List<NodeStats> sharedNodes = nodes.values().stream()
                .filter(node -> node.type != NodeType.USER)
                .filter(node -> node.users.size() > 1)
                .sorted(Comparator.comparingInt((NodeStats n) -> n.users.size()).reversed()
                        .thenComparingInt(n -> n.transactionIds.size()).reversed())
                .collect(Collectors.toList());

        List<FraudGraphPattern> patterns = new ArrayList<>();
        int patternIndex = 1;
        for (NodeStats node : sharedNodes.stream().limit(safeLimit).collect(Collectors.toList())) {
            patterns.add(toPattern(node, transactionScores, transactionVerdicts, patternIndex));
            patternIndex++;
        }

        List<FraudGraphChain> chains = new ArrayList<>();
        int chainIndex = 1;
        for (NodeStats node : sharedNodes.stream().limit(safeLimit).collect(Collectors.toList())) {
            FraudGraphChain chain = toChain(node, transactionScores, transactionVerdicts, chainIndex);
            if (chain != null) {
                chains.add(chain);
                chainIndex++;
            }
        }

        long sharedDevices = sharedNodes.stream().filter(node -> node.type == NodeType.DEVICE).count();
        long sharedIps = sharedNodes.stream().filter(node -> node.type == NodeType.IP).count();
        long sharedMerchants = sharedNodes.stream().filter(node -> node.type == NodeType.MERCHANT).count();
        long sharedLocations = sharedNodes.stream().filter(node -> node.type == NodeType.LOCATION).count();

        log.info("Built fraud graph: transactions={} nodes={} edges={} rings={} patterns={} chains={}",
                transactions.size(),
                nodes.size(),
                countEdges(adjacency),
                rings.size(),
                patterns.size(),
                chains.size());

        return FraudGraphResponse.builder()
                .totalTransactions(transactions.size())
                .totalNodes(nodes.size())
                .totalEdges(countEdges(adjacency))
                .totalComponents(components.size())
                .fraudRingsDetected(rings.size())
                .sharedDevices(sharedDevices)
                .sharedIps(sharedIps)
                .sharedMerchants(sharedMerchants)
                .sharedLocations(sharedLocations)
                .rings(rings)
                .patterns(patterns)
                .chains(chains)
                .build();
    }

    private FraudGraphResponse emptyResponse() {
        return FraudGraphResponse.builder()
                .totalTransactions(0)
                .totalNodes(0)
                .totalEdges(0)
                .totalComponents(0)
                .fraudRingsDetected(0)
                .sharedDevices(0)
                .sharedIps(0)
                .sharedMerchants(0)
                .sharedLocations(0)
                .rings(List.of())
                .patterns(List.of())
                .chains(List.of())
                .build();
    }

    private void addLinkedEntity(
            Map<String, NodeStats> nodes,
            Map<String, Set<String>> adjacency,
            String userKey,
            NodeType type,
            String value,
            Transaction tx,
            boolean flagged,
            double fraudScore) {
        if (value == null || value.isBlank()) {
            return;
        }

        String entityKey = nodeKey(type, value);
        NodeStats entityNode = ensureNode(nodes, entityKey, type, value);
        recordNodeActivity(entityNode, tx, flagged, fraudScore);
        connect(adjacency, userKey, entityKey);
    }

    private NodeStats ensureNode(Map<String, NodeStats> nodes, String key, NodeType type, String value) {
        return nodes.computeIfAbsent(key, ignored -> new NodeStats(key, type, value));
    }

    private void recordNodeActivity(NodeStats node, Transaction tx, boolean flagged, double fraudScore) {
        node.transactionIds.add(tx.getTransactionId());
        node.users.add(tx.getUserId());
        node.fraudScoreTotal += fraudScore;
        node.fraudScoreSamples += 1;
        if (flagged) {
            node.flaggedTransactionIds.add(tx.getTransactionId());
        }
    }

    private List<Component> buildComponents(Map<String, NodeStats> nodes, Map<String, Set<String>> adjacency) {
        List<Component> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String start : nodes.keySet()) {
            if (visited.contains(start)) {
                continue;
            }

            Set<String> componentNodes = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);

            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                componentNodes.add(current);
                for (String next : adjacency.getOrDefault(current, Set.of())) {
                    if (visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }

            components.add(new Component(components.size() + 1, componentNodes));
        }

        return components;
    }

    private FraudGraphRing toRing(
            Component component,
            Map<String, NodeStats> nodes,
            Map<String, Double> transactionScores,
            Map<String, Transaction.FraudVerdict> transactionVerdicts,
            int ringIndex) {

        List<NodeStats> componentNodes = component.nodeKeys.stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long userCount = componentNodes.stream().filter(node -> node.type == NodeType.USER).count();
        long sharedEntityCount = componentNodes.stream()
                .filter(node -> node.type != NodeType.USER)
                .filter(node -> node.users.size() > 1)
                .count();

        Set<String> transactionIds = componentNodes.stream()
                .flatMap(node -> node.transactionIds.stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> flaggedTransactionIds = transactionIds.stream()
                .filter(txId -> isFlagged(transactionVerdicts.get(txId)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (userCount < 2 || sharedEntityCount == 0 || flaggedTransactionIds.isEmpty()) {
            return null;
        }

        double averageFraudScore = transactionIds.stream()
                .map(transactionScores::get)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        List<String> nodeLabels = componentNodes.stream()
                .map(NodeStats::label)
                .sorted()
                .collect(Collectors.toList());

        String summary = String.format(
                "%d users linked through %d shared entities and %d flagged transactions",
                userCount,
                sharedEntityCount,
                flaggedTransactionIds.size());

        return FraudGraphRing.builder()
                .ringId("ring-" + ringIndex)
                .summary(summary)
                .nodeCount(componentNodes.size())
                .userCount((int) userCount)
                .sharedEntityCount((int) sharedEntityCount)
                .transactionCount(transactionIds.size())
                .flaggedTransactionCount(flaggedTransactionIds.size())
                .averageFraudScore(averageFraudScore)
                .nodes(nodeLabels)
                .transactionIds(new ArrayList<>(transactionIds))
                .build();
    }

    private FraudGraphPattern toPattern(
            NodeStats node,
            Map<String, Double> transactionScores,
            Map<String, Transaction.FraudVerdict> transactionVerdicts,
            int patternIndex) {

        Set<String> flaggedTransactionIds = node.transactionIds.stream()
                .filter(txId -> isFlagged(transactionVerdicts.get(txId)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String patternType = switch (node.type) {
            case DEVICE -> "SHARED_DEVICE";
            case IP -> "SHARED_IP";
            case MERCHANT -> "SHARED_MERCHANT";
            case LOCATION -> "SHARED_LOCATION";
            default -> "SHARED_ENTITY";
        };

        String description = String.format(
                "%s used by %d users across %d transactions (%d flagged).",
                humanizePatternType(node.type),
                node.users.size(),
                node.transactionIds.size(),
                flaggedTransactionIds.size());

        return FraudGraphPattern.builder()
                .patternType(patternType)
                .label(node.value)
                .description(description)
                .userCount(node.users.size())
                .transactionCount(node.transactionIds.size())
                .flaggedTransactionCount(flaggedTransactionIds.size())
                .users(new ArrayList<>(node.users))
                .transactionIds(new ArrayList<>(node.transactionIds))
                .build();
    }

    private FraudGraphChain toChain(
            NodeStats node,
            Map<String, Double> transactionScores,
            Map<String, Transaction.FraudVerdict> transactionVerdicts,
            int chainIndex) {

        if (node.users.size() < 2) {
            return null;
        }

        List<String> users = node.users.stream().sorted().collect(Collectors.toList());
        String firstUser = users.get(0);
        String secondUser = users.get(1);

        List<String> path = List.of(
                nodeKey(NodeType.USER, firstUser),
                node.label(),
                nodeKey(NodeType.USER, secondUser)
        );

        Set<String> flaggedTransactionIds = node.transactionIds.stream()
                .filter(txId -> isFlagged(transactionVerdicts.get(txId)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String description = String.format(
                "%s connects %d users across %d transactions (%d flagged).",
                humanizePatternType(node.type),
                node.users.size(),
                node.transactionIds.size(),
                flaggedTransactionIds.size());

        return FraudGraphChain.builder()
                .chainId("chain-" + chainIndex)
                .patternType(node.type.name())
                .description(description)
                .path(path)
                .userCount(node.users.size())
                .transactionCount(node.transactionIds.size())
                .flaggedTransactionCount(flaggedTransactionIds.size())
                .transactionIds(new ArrayList<>(node.transactionIds))
                .build();
    }

    private void connect(Map<String, Set<String>> adjacency, String left, String right) {
        adjacency.computeIfAbsent(left, ignored -> new LinkedHashSet<>()).add(right);
        adjacency.computeIfAbsent(right, ignored -> new LinkedHashSet<>()).add(left);
    }

    private long countEdges(Map<String, Set<String>> adjacency) {
        return adjacency.values().stream().mapToLong(Set::size).sum() / 2;
    }

    private boolean isFlagged(Transaction.FraudVerdict verdict) {
        return verdict == Transaction.FraudVerdict.FRAUD || verdict == Transaction.FraudVerdict.REVIEW;
    }

    private String nodeKey(NodeType type, String value) {
        return type.name() + ":" + value;
    }

    private String locationBucket(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return String.format(Locale.US, "%.2f,%.2f", latitude, longitude);
    }

    private String humanizePatternType(NodeType type) {
        return switch (type) {
            case DEVICE -> "Shared device";
            case IP -> "Shared IP";
            case MERCHANT -> "Shared merchant";
            case LOCATION -> "Shared location";
            case USER -> "Shared user";
        };
    }

    private enum NodeType {
        USER, MERCHANT, DEVICE, IP, LOCATION
    }

    private static final class NodeStats {
        private final String key;
        private final NodeType type;
        private final String value;
        private final Set<String> users = new LinkedHashSet<>();
        private final Set<String> transactionIds = new LinkedHashSet<>();
        private final Set<String> flaggedTransactionIds = new LinkedHashSet<>();
        private double fraudScoreTotal;
        private int fraudScoreSamples;

        private NodeStats(String key, NodeType type, String value) {
            this.key = key;
            this.type = type;
            this.value = value;
        }

        private String label() {
            return key;
        }
    }

    private static final class Component {
        private final int index;
        private final Set<String> nodeKeys;

        private Component(int index, Set<String> nodeKeys) {
            this.index = index;
            this.nodeKeys = nodeKeys;
        }
    }
}
