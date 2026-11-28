# Changelog

## [0.1.6](https://github.com/sequeless/sequeless-filter/compare/filter-core-v0.1.5...filter-core-v0.1.6) (2026-11-28)


### Bug Fixes

* **filter:** is not null resolves to is not, not is ([0beae28](https://github.com/sequeless/sequeless-filter/commit/0beae288da485e5338ffc53760c41f63c54abc43)), closes [#25](https://github.com/sequeless/sequeless-filter/issues/25)
* **filter:** permitted-operators check compares canonical names ([2c0143d](https://github.com/sequeless/sequeless-filter/commit/2c0143dc13fe1f2b3b0cba0c853d7a6f2259dbe8)), closes [#39](https://github.com/sequeless/sequeless-filter/issues/39)

## [0.1.5](https://github.com/sequeless/sequeless-filter/compare/filter-core-v0.1.4...filter-core-v0.1.5) (2026-11-26)


### Features

* **filter:** comparisons apply to date and date-time strings ([763b200](https://github.com/sequeless/sequeless-filter/commit/763b200c6ec074eceb118f7ff9379ba0d0c2f7ae))


### Bug Fixes

* **filter:** keep the existing applicability rule; add a comparison-only route ([f21ba3d](https://github.com/sequeless/sequeless-filter/commit/f21ba3d1b54dd2c545cf3a2805c2a8e05db56768))
* **filter:** the validator keeps the type-only check for existing operators ([7f3923e](https://github.com/sequeless/sequeless-filter/commit/7f3923e59c9d179884a4ad90cc866086772560bb))

## [0.1.4](https://github.com/sequeless/sequeless-filter/compare/filter-core-v0.1.3...filter-core-v0.1.4) (2026-11-10)


### Bug Fixes

* **deps:** pin jackson-annotations to 2.23 and byte-buddy to 1.18.17 ([b18788c](https://github.com/sequeless/sequeless-filter/commit/b18788caeb97817247772c5a6034a86f6fb1515d))

## [0.1.3](https://github.com/sequeless/sequeless-filter/compare/filter-core-v0.1.2...filter-core-v0.1.3) (2026-11-04)


### Bug Fixes

* **filter:** deploy to the sequeless-filter package namespace ([f94ff70](https://github.com/sequeless/sequeless-filter/commit/f94ff7023145d8cab1a8657d710370c9be4cd438))

## [0.1.2](https://github.com/sequeless/sequeless-filter/compare/filter-core-v0.1.1...filter-core-v0.1.2) (2026-11-03)


### Bug Fixes

* **filter-core:** use {[@code](https://github.com/code)} for references to Lombok-generated accessors ([da53b73](https://github.com/sequeless/sequeless-filter/commit/da53b73f5ab9f6636636a09a4e7a4b0a6ec390c9))

## 0.1.1 (2026-11-03)


### Bug Fixes

* **filter-core:** parenthesize nested AND and OR groups when serializing ([413b2ed](https://github.com/sequeless/sequeless-filter/commit/413b2edc549b2406b48572fd38047c0ac959298e))
