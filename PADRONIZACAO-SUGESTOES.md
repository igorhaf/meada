# PADRONIZACAO-SUGESTOES — melhorias que exigiriam decisão/lib nova (NÃO aplicadas)

Registradas conforme a trava "sem dependências novas"; nada aqui foi instalado ou alterado.

1. **Prettier + prettier-plugin-tailwindcss** — hoje a ordem de classes Tailwind é convenção manual
   (documentada na skill `tailwind-styling`). O plugin ordenaria mecanicamente e travaria o padrão
   no CI. Exige devDependency nova.
2. **ESLint no CI + zerar `set-state-in-effect`** — as 60 ocorrências do padrão de sync de
   formulário poderiam virar um hook utilitário (`useSyncedForm(data, toForm)`) aplicado em lote,
   zerando a regra sem duplicação. É mudança comportamental de render: fazer numa frente própria,
   com smoke por tela — não dentro da padronização.
3. **Checkstyle/Spotless no backend** — não há lint Java; um formatter com config mínima (imports,
   indentação) tornaria o cânone verificável. Exige plugin Maven novo.
4. **Extração dos clones de motor (cupom/fidelidade)** — `{Sushi,Adega,Comida,Atelie,Wedding,Barber}Coupon*`
   são clones deliberados por nicho (decisão de arquitetura: isolamento por perfil > DRY). Se um dia
   compensar, um módulo `com.meada.common.coupons` parametrizado por tabela eliminaria ~6× o código —
   decisão do arquiteto, não de padronização.
