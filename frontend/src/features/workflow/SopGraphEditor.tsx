import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import type { SopSpecDocument, SopSpecStep } from './sopSpecModel';

const { Text } = Typography;

const NODE_WIDTH = 180;
const NODE_HEIGHT = 72;

const NODE_TYPE_OPTIONS = [
  { label: 'WORKER', value: 'WORKER' },
  { label: 'CRITIC', value: 'CRITIC' }
];

const JOIN_POLICY_OPTIONS = [
  { label: 'all', value: 'all' },
  { label: 'any', value: 'any' },
  { label: 'quorum', value: 'quorum' }
];

const FAILURE_POLICY_OPTIONS = [
  { label: 'failFast', value: 'failFast' },
  { label: 'failSafe', value: 'failSafe' }
];

const normalizePosition = (step: SopSpecStep, index: number) =>
  step.position || {
    x: (index % 4) * 220 + 30,
    y: Math.floor(index / 4) * 130 + 30
  };

const ensurePositionedSteps = (spec: SopSpecDocument): SopSpecDocument => ({
  ...spec,
  steps: spec.steps.map((step, index) => ({
    ...step,
    position: normalizePosition(step, index)
  }))
});

const updateStep = (spec: SopSpecDocument, stepId: string, patch: Partial<SopSpecStep>): SopSpecDocument => ({
  ...spec,
  steps: spec.steps.map((step) =>
    step.id === stepId
      ? {
          ...step,
          ...patch
        }
      : step
  )
});

const ensureUniqueStepId = (steps: SopSpecStep[]) => {
  const used = new Set(steps.map((step) => step.id));
  let index = steps.length + 1;
  let id = `step_${index}`;
  while (used.has(id)) {
    index += 1;
    id = `step_${index}`;
  }
  return id;
};

interface DragState {
  stepId: string;
  pointerStartX: number;
  pointerStartY: number;
  nodeStartX: number;
  nodeStartY: number;
}

export interface SopGraphEditorProps {
  value: SopSpecDocument;
  onChange: (next: SopSpecDocument) => void;
}

export const SopGraphEditor = ({ value, onChange }: SopGraphEditorProps) => {
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [pendingSource, setPendingSource] = useState<string>();
  const [pendingTarget, setPendingTarget] = useState<string>();
  const [dragState, setDragState] = useState<DragState>();
  const valueRef = useRef(value);

  useEffect(() => {
    valueRef.current = ensurePositionedSteps(value);
  }, [value]);

  const positionedSpec = useMemo(() => ensurePositionedSteps(value), [value]);
  const nodeMap = useMemo(
    () =>
      new Map(
        positionedSpec.steps.map((step) => [
          step.id,
          {
            step,
            x: step.position?.x || 0,
            y: step.position?.y || 0
          }
        ])
      ),
    [positionedSpec.steps]
  );

  useEffect(() => {
    if (!dragState) {
      return;
    }

    const handleMove = (event: MouseEvent) => {
      const current = valueRef.current;
      const dx = event.clientX - dragState.pointerStartX;
      const dy = event.clientY - dragState.pointerStartY;
      const nextX = Math.max(0, dragState.nodeStartX + dx);
      const nextY = Math.max(0, dragState.nodeStartY + dy);

      const next = updateStep(current, dragState.stepId, {
        position: { x: nextX, y: nextY }
      });
      valueRef.current = next;
      onChange(next);
    };

    const handleUp = () => {
      setDragState(undefined);
    };

    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
    };
  }, [dragState, onChange]);

  const selectedStep = useMemo(
    () => positionedSpec.steps.find((step) => step.id === selectedNodeId),
    [positionedSpec.steps, selectedNodeId]
  );

  const allStepOptions = positionedSpec.steps.map((step) => ({ label: `${step.name} (${step.id})`, value: step.id }));

  const addNode = () => {
    const nextId = ensureUniqueStepId(positionedSpec.steps);
    const next: SopSpecDocument = {
      ...positionedSpec,
      steps: [
        ...positionedSpec.steps,
        {
          id: nextId,
          name: `步骤 ${positionedSpec.steps.length + 1}`,
          roleType: 'WORKER',
          dependsOn: [],
          config: {},
          position: {
            x: (positionedSpec.steps.length % 4) * 220 + 30,
            y: Math.floor(positionedSpec.steps.length / 4) * 130 + 30
          }
        }
      ]
    };
    valueRef.current = next;
    onChange(next);
    setSelectedNodeId(nextId);
  };

  const removeNode = () => {
    if (!selectedNodeId) {
      return;
    }
    const next: SopSpecDocument = {
      ...positionedSpec,
      steps: positionedSpec.steps
        .filter((step) => step.id !== selectedNodeId)
        .map((step) => ({
          ...step,
          dependsOn: step.dependsOn.filter((dep) => dep !== selectedNodeId)
        }))
    };
    valueRef.current = next;
    onChange(next);
    setSelectedNodeId(undefined);
  };

  const patchSelectedStep = (patch: Partial<SopSpecStep>) => {
    if (!selectedNodeId) {
      return;
    }
    const next = updateStep(positionedSpec, selectedNodeId, patch);
    valueRef.current = next;
    onChange(next);
  };

  const addDependency = () => {
    if (!pendingSource || !pendingTarget || pendingSource === pendingTarget) {
      return;
    }
    const target = positionedSpec.steps.find((step) => step.id === pendingTarget);
    if (!target) {
      return;
    }
    if (target.dependsOn.includes(pendingSource)) {
      return;
    }
    const next = updateStep(positionedSpec, pendingTarget, {
      dependsOn: [...target.dependsOn, pendingSource]
    });
    valueRef.current = next;
    onChange(next);
  };

  const removeDependency = (source: string, target: string) => {
    const step = positionedSpec.steps.find((item) => item.id === target);
    if (!step) {
      return;
    }
    const next = updateStep(positionedSpec, target, {
      dependsOn: step.dependsOn.filter((item) => item !== source)
    });
    valueRef.current = next;
    onChange(next);
  };

  return (
    <div className="sop-editor-layout">
      <div className="sop-editor-canvas">
        <Space wrap style={{ marginBottom: 8 }}>
          <Button onClick={addNode}>新增节点</Button>
          <Button danger disabled={!selectedNodeId} onClick={removeNode}>
            删除节点
          </Button>
          <Select
            style={{ width: 220 }}
            placeholder="依赖源节点"
            value={pendingSource}
            options={allStepOptions}
            allowClear
            onChange={setPendingSource}
          />
          <Select
            style={{ width: 220 }}
            placeholder="目标节点"
            value={pendingTarget}
            options={allStepOptions}
            allowClear
            onChange={setPendingTarget}
          />
          <Button onClick={addDependency} disabled={!pendingSource || !pendingTarget || pendingSource === pendingTarget}>
            添加依赖连线
          </Button>
          <Text type="secondary">拖拽节点可调整布局。</Text>
        </Space>

        <div className="sop-flow-wrapper">
          <svg className="sop-edge-layer" width="100%" height="100%">
            <defs>
              <marker id="sop-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6 L7,3 z" fill="#8c8c8c" />
              </marker>
            </defs>
            {positionedSpec.steps.flatMap((target) =>
              target.dependsOn.map((sourceId) => {
                const source = nodeMap.get(sourceId);
                const to = nodeMap.get(target.id);
                if (!source || !to) {
                  return null;
                }
                const x1 = source.x + NODE_WIDTH;
                const y1 = source.y + NODE_HEIGHT / 2;
                const x2 = to.x;
                const y2 = to.y + NODE_HEIGHT / 2;
                const key = `${sourceId}->${target.id}`;
                const selected = selectedNodeId && (selectedNodeId === sourceId || selectedNodeId === target.id);
                return (
                  <g key={key}>
                    <path
                      d={`M ${x1} ${y1} C ${x1 + 40} ${y1}, ${x2 - 40} ${y2}, ${x2} ${y2}`}
                      stroke={selected ? '#1677ff' : '#8c8c8c'}
                      strokeWidth={selected ? 1.6 : 1.1}
                      fill="none"
                      markerEnd="url(#sop-arrow)"
                    />
                    <title>{`${sourceId} -> ${target.id}`}</title>
                  </g>
                );
              })
            )}
          </svg>

          {positionedSpec.steps.map((step, index) => {
            const position = normalizePosition(step, index);
            const isSelected = selectedNodeId === step.id;
            return (
              <div
                key={step.id}
                className={`sop-node-card ${isSelected ? 'selected' : ''}`}
                style={{
                  left: position.x,
                  top: position.y,
                  borderColor: isSelected ? '#1677ff' : '#d9d9d9',
                  background: step.roleType === 'CRITIC' ? '#fff7e6' : '#f6ffed'
                }}
                onMouseDown={(event) => {
                  if (event.button !== 0) {
                    return;
                  }
                  setSelectedNodeId(step.id);
                  setDragState({
                    stepId: step.id,
                    pointerStartX: event.clientX,
                    pointerStartY: event.clientY,
                    nodeStartX: position.x,
                    nodeStartY: position.y
                  });
                }}
              >
                <div className="sop-node-title">{step.name || step.id}</div>
                <div className="sop-node-subtitle">
                  <Tag color={step.roleType === 'CRITIC' ? 'orange' : 'green'}>{step.roleType}</Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {step.id}
                  </Text>
                </div>
              </div>
            );
          })}
        </div>

        {selectedStep ? (
          <Card size="small" style={{ marginTop: 12 }} title={`节点依赖：${selectedStep.id}`}>
            {selectedStep.dependsOn.length === 0 ? (
              <Text type="secondary">暂无依赖</Text>
            ) : (
              <Space wrap>
                {selectedStep.dependsOn.map((dep) => (
                  <Tag key={dep} closable onClose={() => removeDependency(dep, selectedStep.id)}>
                    {dep}
                  </Tag>
                ))}
              </Space>
            )}
          </Card>
        ) : null}
      </div>

      <Card size="small" title="节点属性" className="sop-editor-panel">
        {!selectedStep ? (
          <Alert type="info" showIcon message="请选择一个节点后编辑属性" />
        ) : (
          <Form layout="vertical" size="small">
            <Form.Item label="节点ID">
              <Input value={selectedStep.id} disabled />
            </Form.Item>
            <Form.Item label="节点名称">
              <Input
                value={selectedStep.name}
                onChange={(event) => patchSelectedStep({ name: event.target.value })}
                placeholder="请输入节点名称"
              />
            </Form.Item>
            <Form.Item label="节点类型">
              <Select
                value={selectedStep.roleType}
                options={NODE_TYPE_OPTIONS}
                onChange={(value) => patchSelectedStep({ roleType: value })}
              />
            </Form.Item>
            <Form.Item label="分组ID（可选）">
              <Input
                value={selectedStep.groupId}
                onChange={(event) => patchSelectedStep({ groupId: event.target.value || undefined })}
                placeholder="例如: research-group"
              />
            </Form.Item>
            <Form.Item label="joinPolicy（可选）">
              <Select
                allowClear
                value={selectedStep.joinPolicy}
                options={JOIN_POLICY_OPTIONS}
                onChange={(value) => patchSelectedStep({ joinPolicy: value || undefined })}
              />
            </Form.Item>
            <Form.Item label="failurePolicy（可选）">
              <Select
                allowClear
                value={selectedStep.failurePolicy}
                options={FAILURE_POLICY_OPTIONS}
                onChange={(value) => patchSelectedStep({ failurePolicy: value || undefined })}
              />
            </Form.Item>
            <Form.Item label="quorum（可选）">
              <InputNumber
                style={{ width: '100%' }}
                min={1}
                value={selectedStep.quorum}
                onChange={(value) => patchSelectedStep({ quorum: typeof value === 'number' ? value : undefined })}
              />
            </Form.Item>
          </Form>
        )}
      </Card>
    </div>
  );
};
